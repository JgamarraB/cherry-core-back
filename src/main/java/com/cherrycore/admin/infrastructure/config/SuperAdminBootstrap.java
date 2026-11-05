package com.cherrycore.admin.infrastructure.config;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.company.domain.StaffUser;
import com.cherrycore.identity.application.PasswordResetService;
import com.cherrycore.identity.application.RequestContext;
import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.PasswordHasher;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.domain.PlatformRole;
import com.cherrycore.shared.domain.id.UuidV7;
import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

/**
 * Crea el primer superadmin al arrancar, si no hay ninguno y está configurado
 * {@code cherrycore.bootstrap.super-admin-email}. Recibe un email para elegir
 * su contraseña; al entrar tendrá que configurar el MFA.
 * Si ya hay superadmins, no hace nada.
 */
@Component
class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuperAdminBootstrap.class);
    private static final String LOG_CREATED = "First super admin created; a password link has been emailed";
    private static final RequestContext SYSTEM = new RequestContext(null, "bootstrap");
    private static final String FIRST_NAME = "Super";
    private static final String LAST_NAME = "Admin";
    private static final int RANDOM_PASSWORD_BYTES = 48;

    private final AccountRepository accounts;
    private final StaffUserRepository staffUsers;
    private final PasswordHasher passwordHasher;
    private final PasswordResetService passwordReset;
    private final TransactionTemplate transaction;
    private final CherryCoreProperties properties;

    SuperAdminBootstrap(AccountRepository accounts, StaffUserRepository staffUsers, PasswordHasher passwordHasher,
            PasswordResetService passwordReset, TransactionTemplate transaction, CherryCoreProperties properties) {
        this.accounts = accounts;
        this.staffUsers = staffUsers;
        this.passwordHasher = passwordHasher;
        this.passwordReset = passwordReset;
        this.transaction = transaction;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.bootstrap() == null ? null : properties.bootstrap().superAdminEmail();
        if (email == null || email.isBlank() || !accounts.findSuperAdmins().isEmpty()) {
            return;
        }
        transaction.executeWithoutResult(status -> {
            EmailAddress address = EmailAddress.of(email);
            Account admin = accounts.findByTypeAndEmail(AccountType.STAFF, address).orElseGet(() -> {
                Account created = Account.newInvitedStaff(UuidV7.generate(), address,
                        passwordHasher.hash(unguessablePassword()), properties.i18n().defaultLocale(),
                        Instant.now());
                accounts.save(created);
                staffUsers.save(new StaffUser(created.id(), PersonName.of(FIRST_NAME), PersonName.of(LAST_NAME),
                        null));
                return created;
            });
            if (admin.isPendingVerification()) {
                // Una cuenta sin verificar pudo crearla cualquiera: su contraseña no vale.
                admin.removePassword();
            }
            admin.grantPlatformRole(PlatformRole.SUPER_ADMIN);
            accounts.save(admin);
            passwordReset.requestFor(admin.id(), SYSTEM);
        });
        LOGGER.info(LOG_CREATED);
    }

    /* Contraseña que nadie conoce: el superadmin elige la suya con el enlace del email. */
    private static String unguessablePassword() {
        byte[] bytes = new byte[RANDOM_PASSWORD_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toLowerCase(Locale.ROOT);
    }
}
