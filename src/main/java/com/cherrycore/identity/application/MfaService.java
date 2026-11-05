package com.cherrycore.identity.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.MfaRepository;
import com.cherrycore.identity.application.port.RecoveryCodeHasher;
import com.cherrycore.identity.application.port.SecretEncryptor;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.Base32;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.identity.domain.MfaFactor;
import com.cherrycore.identity.domain.RecoveryCode;
import com.cherrycore.identity.domain.Totp;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.id.UuidV7;

/**
 * Verificación en dos pasos con app autenticadora (TOTP).
 * Obligatoria para backoffice; opcional para clientes.
 */
@Service
public class MfaService {

    public static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_LENGTH = 10;
    private static final char[] RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();
    private static final String OTPAUTH_FORMAT = "otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d";
    private static final String RECOVERY_SEPARATOR = "-";
    private static final int RECOVERY_GROUP = 5;
    private static final String NOTHING = "";
    private static final RequestContext ANONYMOUS_CONTEXT = new RequestContext(null, null);

    private final MfaRepository mfaRepository;
    private final AccountRepository accounts;
    private final SecretEncryptor encryptor;
    private final RecoveryCodeHasher hasher;
    private final IdentitySettings settings;
    private final AccountLockoutService lockout;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MfaService(MfaRepository mfaRepository, AccountRepository accounts, SecretEncryptor encryptor,
            RecoveryCodeHasher hasher, IdentitySettings settings, AccountLockoutService lockout, Clock clock) {
        this.lockout = lockout;
        this.mfaRepository = mfaRepository;
        this.accounts = accounts;
        this.encryptor = encryptor;
        this.hasher = hasher;
        this.settings = settings;
        this.clock = clock;
    }

    public static boolean isMandatoryFor(AccountType type) {
        return type == AccountType.STAFF;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID accountId) {
        return mfaRepository.findFactor(accountId).filter(MfaFactor::isConfirmed).isPresent();
    }

    @Transactional(readOnly = true)
    public MfaStatus status(UUID accountId) {
        Account account = account(accountId);
        int left = (int) mfaRepository.findRecoveryCodes(accountId).stream().filter(RecoveryCode::isUnused).count();
        return new MfaStatus(isEnabled(accountId), isMandatoryFor(account.type()), left);
    }

    /**
     * Empieza el alta: genera un secreto nuevo (sin confirmar).
     */
    @Transactional
    public MfaEnrollment startEnrollment(UUID accountId) {
        Account account = account(accountId);
        if (isEnabled(accountId)) {
            throw new DomainException(IdentityErrorCode.MFA_ALREADY_ENABLED);
        }
        byte[] secret = Totp.generateSecret();
        mfaRepository.deleteFactor(accountId);
        mfaRepository.save(new MfaFactor(UuidV7.generate(clock), accountId, encryptor.encrypt(secret), null, null));
        String base32 = Base32.encode(secret);
        return new MfaEnrollment(base32, otpauthUri(account, base32));
    }

    /**
     * Confirma el alta con el primer código. Devuelve los códigos de
     * recuperación: se muestran una sola vez.
     */
    @Transactional
    public List<String> confirmEnrollment(UUID accountId, String code) {
        MfaFactor factor = mfaRepository.findFactorForUpdate(accountId)
                .orElseThrow(() -> new DomainException(IdentityErrorCode.MFA_SETUP_NOT_STARTED));
        if (factor.isConfirmed()) {
            throw new DomainException(IdentityErrorCode.MFA_ALREADY_ENABLED);
        }
        Instant now = clock.instant();
        long step = Totp.verify(encryptor.decrypt(factor.encryptedSecret()), code, now, OptionalLong.empty())
                .orElseThrow(() -> new DomainException(IdentityErrorCode.INVALID_MFA_CODE));
        factor.confirm(now, step);
        mfaRepository.save(factor);
        return newRecoveryCodes(accountId);
    }

    /**
     * Comprueba un código de la app o un código de recuperación.
     */
    @Transactional
    public boolean verify(UUID accountId, String code) {
        Optional<MfaFactor> factor = mfaRepository.findFactorForUpdate(accountId).filter(MfaFactor::isConfirmed);
        if (factor.isEmpty() || code == null) {
            return false;
        }
        String cleaned = code.strip();
        byte[] secret = encryptor.decrypt(factor.get().encryptedSecret());
        OptionalLong step = Totp.verify(secret, cleaned, clock.instant(), factor.get().lastUsed());
        if (step.isPresent()) {
            factor.get().recordUse(step.getAsLong());
            if (!encryptor.isCurrent(factor.get().encryptedSecret())) {
                // Cifrado con la clave anterior: se pasa a la actual (rotación).
                factor.get().reencrypt(encryptor.encrypt(secret));
            }
            mfaRepository.save(factor.get());
            return true;
        }
        return useRecoveryCode(accountId, cleaned);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(UUID accountId, String code) {
        requireValidCode(accountId, code);
        return newRecoveryCodes(accountId);
    }

    /**
     * Desactivar el MFA (solo clientes; en backoffice es obligatorio).
     */
    @Transactional
    public void disable(UUID accountId, String code) {
        if (isMandatoryFor(account(accountId).type())) {
            throw new DomainException(IdentityErrorCode.MFA_REQUIRED_FOR_STAFF);
        }
        requireValidCode(accountId, code);
        reset(accountId);
    }

    /**
     * Quita el MFA sin código (superadmin, cuando alguien pierde el móvil y
     * los códigos). La próxima vez que entre tendrá que configurarlo de nuevo.
     */
    @Transactional
    public void reset(UUID accountId) {
        mfaRepository.deleteFactor(accountId);
        mfaRepository.replaceRecoveryCodes(accountId, List.of());
    }

    private void requireValidCode(UUID accountId, String code) {
        if (!isEnabled(accountId)) {
            throw new DomainException(IdentityErrorCode.MFA_NOT_ENABLED);
        }
        if (lockout.isLocked(accountId) || !verify(accountId, code)) {
            lockout.recordFailure(accountId, AuthEventType.MFA_FAILED, ANONYMOUS_CONTEXT);
            throw new DomainException(IdentityErrorCode.INVALID_MFA_CODE);
        }
    }

    private boolean useRecoveryCode(UUID accountId, String code) {
        String hash = hasher.hash(normalizeRecoveryCode(code));
        return mfaRepository.findRecoveryCodes(accountId).stream()
                .filter(candidate -> candidate.isUnused() && candidate.codeHash().equals(hash))
                .findFirst()
                .map(candidate -> {
                    candidate.markUsed(clock.instant());
                    mfaRepository.save(candidate);
                    return true;
                })
                .orElse(false);
    }

    private List<String> newRecoveryCodes(UUID accountId) {
        List<String> plain = new ArrayList<>();
        List<RecoveryCode> hashed = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomRecoveryCode();
            plain.add(code);
            hashed.add(new RecoveryCode(UuidV7.generate(clock), accountId, hasher.hash(normalizeRecoveryCode(code)),
                    null));
        }
        mfaRepository.replaceRecoveryCodes(accountId, hashed);
        return plain;
    }

    private String randomRecoveryCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < RECOVERY_CODE_LENGTH; i++) {
            if (i == RECOVERY_GROUP) {
                code.append(RECOVERY_SEPARATOR);
            }
            code.append(RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)]);
        }
        return code.toString();
    }

    private static String normalizeRecoveryCode(String code) {
        return code.replace(RECOVERY_SEPARATOR, NOTHING).toLowerCase(Locale.ROOT);
    }

    private String otpauthUri(Account account, String base32Secret) {
        String issuer = URLEncoder.encode(settings.mfaIssuer(), StandardCharsets.UTF_8);
        String label = URLEncoder.encode(account.email().value(), StandardCharsets.UTF_8);
        return OTPAUTH_FORMAT.formatted(issuer, label, base32Secret, issuer, Totp.DIGITS, Totp.STEP_SECONDS);
    }

    private Account account(UUID accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new DomainException(IdentityErrorCode.ACCOUNT_NOT_FOUND));
    }
}
