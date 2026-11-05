package com.cherrycore.identity.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.AccountSessionRevoker;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuditDetail;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.identity.domain.PlatformRole;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Acciones del superadmin sobre cualquier cuenta. Todas quedan en la auditoría.
 * Un superadmin no puede desactivarse ni quitarse el rol a sí mismo, y siempre
 * queda al menos un superadmin.
 */
@Service
public class AccountAdministrationService {

    private final AccountRepository accounts;
    private final AccountSessionRevoker sessionRevoker;
    private final MfaService mfa;
    private final PasswordResetService passwordReset;
    private final AuthEventLogger eventLogger;

    public AccountAdministrationService(AccountRepository accounts, AccountSessionRevoker sessionRevoker,
            MfaService mfa, PasswordResetService passwordReset, AuthEventLogger eventLogger) {
        this.accounts = accounts;
        this.sessionRevoker = sessionRevoker;
        this.mfa = mfa;
        this.passwordReset = passwordReset;
        this.eventLogger = eventLogger;
    }

    @Transactional
    public Account disable(UUID adminId, UUID accountId, RequestContext context) {
        Account account = otherAccount(adminId, accountId);
        if (account.isSuperAdmin()) {
            requireAnotherSuperAdmin(account);
        }
        account.disable();
        accounts.save(account);
        sessionRevoker.revokeAll(accountId);
        eventLogger.log(AuthEventType.ACCOUNT_DISABLED_BY_ADMIN, accountId, context, actor(adminId));
        return account;
    }

    @Transactional
    public Account enable(UUID adminId, UUID accountId, RequestContext context) {
        Account account = otherAccount(adminId, accountId);
        account.reactivate();
        accounts.save(account);
        eventLogger.log(AuthEventType.ACCOUNT_ENABLED_BY_ADMIN, accountId, context, actor(adminId));
        return account;
    }

    @Transactional
    public Account unlock(UUID adminId, UUID accountId, RequestContext context) {
        Account account = find(accountId);
        account.unlock();
        accounts.save(account);
        eventLogger.log(AuthEventType.ACCOUNT_UNLOCKED_BY_ADMIN, accountId, context, actor(adminId));
        return account;
    }

    /**
     * Quita el MFA (perdió el móvil y los códigos). Cierra sus sesiones: al
     * volver a entrar tendrá que configurarlo de nuevo si es backoffice.
     */
    @Transactional
    public void resetMfa(UUID adminId, UUID accountId, RequestContext context) {
        otherAccount(adminId, accountId);
        mfa.reset(accountId);
        sessionRevoker.revokeAll(accountId);
        eventLogger.log(AuthEventType.MFA_RESET_BY_ADMIN, accountId, context, actor(adminId));
    }

    @Transactional
    public void sendPasswordReset(UUID adminId, UUID accountId, RequestContext context) {
        passwordReset.requestFor(find(accountId).id(), context);
        eventLogger.log(AuthEventType.PASSWORD_RESET_SENT_BY_ADMIN, accountId, context, actor(adminId));
    }

    @Transactional
    public Account setSuperAdmin(UUID adminId, UUID accountId, boolean superAdmin, RequestContext context) {
        Account account = otherAccount(adminId, accountId);
        if (account.type() != AccountType.STAFF) {
            throw new DomainException(IdentityErrorCode.NOT_A_STAFF_ACCOUNT);
        }
        boolean wasSuperAdmin = account.isSuperAdmin();
        if (superAdmin) {
            account.grantPlatformRole(PlatformRole.SUPER_ADMIN);
        } else if (wasSuperAdmin) {
            requireAnotherSuperAdmin(account);
            account.revokePlatformRole();
        }
        accounts.save(account);
        if (superAdmin != wasSuperAdmin) {
            eventLogger.log(superAdmin ? AuthEventType.SUPER_ADMIN_GRANTED : AuthEventType.SUPER_ADMIN_REVOKED,
                    accountId, context, actor(adminId));
        }
        // El rol va en la sesión: se cierran sus sesiones para aplicarlo.
        sessionRevoker.revokeAll(accountId);
        return account;
    }

    private static Map<String, String> actor(UUID adminId) {
        return Map.of(AuditDetail.ACTOR_ACCOUNT_ID, adminId.toString());
    }

    private void requireAnotherSuperAdmin(Account account) {
        boolean another = accounts.findSuperAdmins().stream()
                .anyMatch(admin -> admin.isActive() && !admin.id().equals(account.id()));
        if (!another) {
            throw new DomainException(IdentityErrorCode.LAST_SUPER_ADMIN);
        }
    }

    private Account otherAccount(UUID adminId, UUID accountId) {
        if (adminId.equals(accountId)) {
            throw new DomainException(IdentityErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
        }
        return find(accountId);
    }

    private Account find(UUID accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new DomainException(IdentityErrorCode.ACCOUNT_NOT_FOUND));
    }
}
