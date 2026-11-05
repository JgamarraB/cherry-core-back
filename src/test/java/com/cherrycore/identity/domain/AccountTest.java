package com.cherrycore.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AccountTest {

    private static final LoginPolicy POLICY = new LoginPolicy(3, Duration.ofMinutes(15));
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    private Account newAccount() {
        return Account.newCustomer(UUID.randomUUID(), EmailAddress.of("ana@example.com"), "{argon2}hash",
                Locale.forLanguageTag("es"));
    }

    @Test
    void newCustomerIsPendingUntilEmailIsVerified() {
        Account account = newAccount();
        assertThat(account.isPendingVerification()).isTrue();

        account.verifyEmail(NOW);

        assertThat(account.isActive()).isTrue();
        assertThat(account.emailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void locksAfterMaxFailedAttemptsAndUnlocksWhenTimePasses() {
        Account account = newAccount();

        assertThat(account.registerFailedLogin(NOW, POLICY)).isFalse();
        assertThat(account.registerFailedLogin(NOW, POLICY)).isFalse();
        assertThat(account.registerFailedLogin(NOW, POLICY)).isTrue();

        assertThat(account.isLockedAt(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(account.isLockedAt(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void counterRestartsAfterAnExpiredLock() {
        Account account = newAccount();
        for (int i = 0; i < 3; i++) {
            account.registerFailedLogin(NOW, POLICY);
        }
        Instant afterLock = NOW.plus(Duration.ofHours(1));

        assertThat(account.registerFailedLogin(afterLock, POLICY)).isFalse();
        assertThat(account.isLockedAt(afterLock)).isFalse();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        Account account = newAccount();
        account.registerFailedLogin(NOW, POLICY);
        account.registerFailedLogin(NOW, POLICY);

        account.registerSuccessfulLogin(NOW);

        assertThat(account.failedLoginAttempts()).isZero();
        assertThat(account.registerFailedLogin(NOW, POLICY)).isFalse();
    }

    @Test
    void passwordResetUnlocksAndProvesEmailOwnership() {
        Account account = newAccount();
        for (int i = 0; i < 3; i++) {
            account.registerFailedLogin(NOW, POLICY);
        }

        account.resetPassword("{argon2}new", NOW);

        assertThat(account.isLockedAt(NOW)).isFalse();
        assertThat(account.isActive()).isTrue();
    }
}
