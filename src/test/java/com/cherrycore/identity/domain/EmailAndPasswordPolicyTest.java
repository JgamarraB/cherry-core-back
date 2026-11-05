package com.cherrycore.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cherrycore.shared.domain.error.DomainException;

class EmailAndPasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(10, 128);

    @Test
    void emailIsNormalized() {
        assertThat(EmailAddress.of("  Ana.Garcia@Example.COM ").value()).isEqualTo("ana.garcia@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ana", "ana@", "@example.com", "ana@example", "an a@example.com", "a@b.c"})
    void invalidEmailsAreRejected(String raw) {
        assertThatThrownBy(() -> EmailAddress.of(raw))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).getErrorCode())
                .isEqualTo(IdentityErrorCode.INVALID_EMAIL);
    }

    @Test
    void passwordLengthIsCountedInCharactersNotBytes() {
        // 10 emojis = 10 caracteres (20 unidades UTF-16).
        policy.validate("😀😀😀😀😀😀😀😀😀😀", EmailAddress.of("ana@example.com"));
    }

    @Test
    void passwordRules() {
        EmailAddress email = EmailAddress.of("lucia.perez@example.com");
        assertError(() -> policy.validate("corta", email), IdentityErrorCode.PASSWORD_TOO_SHORT);
        assertError(() -> policy.validate("x".repeat(129), email), IdentityErrorCode.PASSWORD_TOO_LONG);
        assertError(() -> policy.validate("LUCIA.PEREZ-2026", email), IdentityErrorCode.PASSWORD_CONTAINS_EMAIL);
        policy.validate("una frase larga y segura", email);
    }

    private static void assertError(Runnable action, IdentityErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).getErrorCode())
                .isEqualTo(expected);
    }
}
