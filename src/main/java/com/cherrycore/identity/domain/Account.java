package com.cherrycore.identity.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuenta de acceso: todo lo relacionado con la seguridad del login.
 * Los datos personales viven en el perfil (customers, staff_users).
 */
public class Account {

    private final UUID id;
    private final AccountType type;
    private EmailAddress email;
    private Instant emailVerifiedAt;
    private String passwordHash;
    private AccountStatus status;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private PlatformRole platformRole;
    private Locale locale;

    private Account(UUID id, AccountType type, EmailAddress email, Instant emailVerifiedAt, String passwordHash,
            AccountStatus status, int failedLoginAttempts, Instant lockedUntil, Instant lastLoginAt,
            PlatformRole platformRole, Locale locale) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.email = Objects.requireNonNull(email);
        this.emailVerifiedAt = emailVerifiedAt;
        this.passwordHash = passwordHash;
        this.status = Objects.requireNonNull(status);
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.platformRole = platformRole;
        this.locale = Objects.requireNonNull(locale);
    }

    /**
     * Nueva cuenta de cliente: queda pendiente hasta verificar el email.
     */
    public static Account newCustomer(UUID id, EmailAddress email, String passwordHash, Locale locale) {
        return new Account(id, AccountType.CUSTOMER, email, null, Objects.requireNonNull(passwordHash),
                AccountStatus.PENDING_VERIFICATION, 0, null, null, null, locale);
    }

    /**
     * Nueva cuenta de backoffice que se registra sola: pendiente hasta verificar el email.
     */
    public static Account newStaff(UUID id, EmailAddress email, String passwordHash, Locale locale) {
        return new Account(id, AccountType.STAFF, email, null, Objects.requireNonNull(passwordHash),
                AccountStatus.PENDING_VERIFICATION, 0, null, null, null, locale);
    }

    /**
     * Cuenta de backoffice creada al aceptar una invitación: el enlace llegó a
     * su email, así que el email ya está verificado.
     */
    public static Account newInvitedStaff(UUID id, EmailAddress email, String passwordHash, Locale locale,
            Instant now) {
        return new Account(id, AccountType.STAFF, email, now, Objects.requireNonNull(passwordHash),
                AccountStatus.ACTIVE, 0, null, null, null, locale);
    }

    /**
     * Cliente que se registra con Google/Apple: el proveedor ha verificado el
     * email y no tiene contraseña (puede crearla con "olvidé mi contraseña").
     */
    public static Account newSocialCustomer(UUID id, EmailAddress email, Locale locale, Instant now) {
        return new Account(id, AccountType.CUSTOMER, email, now, null, AccountStatus.ACTIVE, 0, null, null, null,
                locale);
    }

    public static Account restore(UUID id, AccountType type, EmailAddress email, Instant emailVerifiedAt,
            String passwordHash, AccountStatus status, int failedLoginAttempts, Instant lockedUntil,
            Instant lastLoginAt, PlatformRole platformRole, Locale locale) {
        return new Account(id, type, email, emailVerifiedAt, passwordHash, status, failedLoginAttempts,
                lockedUntil, lastLoginAt, platformRole, locale);
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isPendingVerification() {
        return status == AccountStatus.PENDING_VERIFICATION;
    }

    public boolean isDisabled() {
        return status == AccountStatus.DISABLED;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public void verifyEmail(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
        }
        if (status == AccountStatus.PENDING_VERIFICATION) {
            status = AccountStatus.ACTIVE;
        }
    }

    /**
     * Registra un intento fallido. Al llegar al máximo, bloquea temporalmente.
     *
     * @return {@code true} si este intento ha provocado el bloqueo
     */
    public boolean registerFailedLogin(Instant now, LoginPolicy policy) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            // El bloqueo anterior ya expiró: se empieza a contar de nuevo.
            failedLoginAttempts = 0;
            lockedUntil = null;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= policy.maxFailedAttempts()) {
            lockedUntil = now.plus(policy.lockDuration());
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /**
     * Cambio de contraseña desde un enlace enviado al email: además demuestra
     * que el email es suyo, así que se da por verificado.
     */
    public void resetPassword(String newPasswordHash, Instant now) {
        passwordHash = Objects.requireNonNull(newPasswordHash);
        failedLoginAttempts = 0;
        lockedUntil = null;
        verifyEmail(now);
    }

    public void grantPlatformRole(PlatformRole role) {
        if (type != AccountType.STAFF) {
            throw new IllegalStateException("Platform roles are only for staff accounts");
        }
        platformRole = Objects.requireNonNull(role);
    }

    public boolean isSuperAdmin() {
        return platformRole == PlatformRole.SUPER_ADMIN;
    }

    /**
     * Desactivación por un superadmin: ya no puede entrar ni renovar tokens.
     */
    public void disable() {
        status = AccountStatus.DISABLED;
    }

    /**
     * Reactivación: vuelve a activa si tenía el email verificado, si no a pendiente.
     */
    public void reactivate() {
        status = emailVerifiedAt == null ? AccountStatus.PENDING_VERIFICATION : AccountStatus.ACTIVE;
    }

    public void unlock() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /**
     * Email nuevo confirmado con el enlace enviado a ese email.
     */
    public void changeEmail(EmailAddress newEmail, Instant now) {
        email = Objects.requireNonNull(newEmail);
        emailVerifiedAt = now;
    }

    public void changePassword(String newPasswordHash) {
        passwordHash = Objects.requireNonNull(newPasswordHash);
    }

    /**
     * Borrado de cuenta (RGPD): se eliminan los datos personales y la cuenta
     * queda desactivada. El email queda libre para registrarse de nuevo.
     */
    public void anonymize(EmailAddress placeholderEmail) {
        email = Objects.requireNonNull(placeholderEmail);
        passwordHash = null;
        status = AccountStatus.DISABLED;
        platformRole = null;
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void changeLocale(Locale newLocale) {
        locale = Objects.requireNonNull(newLocale);
    }

    public UUID id() {
        return id;
    }

    public AccountType type() {
        return type;
    }

    public EmailAddress email() {
        return email;
    }

    public Instant emailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public AccountStatus status() {
        return status;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public PlatformRole platformRole() {
        return platformRole;
    }

    public Locale locale() {
        return locale;
    }
}
