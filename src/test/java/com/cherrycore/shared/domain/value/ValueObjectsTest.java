package com.cherrycore.shared.domain.value;

import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.domain.value.PhoneNumber;
import com.cherrycore.shared.domain.value.CountryCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cherrycore.shared.domain.error.DomainException;

class ValueObjectsTest {

    @Test
    void phoneIsNormalizedToE164() {
        assertThat(PhoneNumber.of("+34 600-11 (12) 22").value()).isEqualTo("+34600111222");
    }

    @ParameterizedTest
    @ValueSource(strings = {"600111222", "+0034600111222", "+34", "+34abc"})
    void invalidPhonesAreRejected(String raw) {
        assertThatThrownBy(() -> PhoneNumber.of(raw)).isInstanceOf(DomainException.class);
    }

    @Test
    void countryCodeMustBeRealIso() {
        assertThat(CountryCode.of(" es ").value()).isEqualTo("ES");
        assertThatThrownBy(() -> CountryCode.of("XX")).isInstanceOf(DomainException.class);
    }

    @Test
    void namesRejectControlCharacters() {
        assertThat(PersonName.of("  Ana ").value()).isEqualTo("Ana");
        assertThatThrownBy(() -> PersonName.of("Ana\r\nBcc: x@evil.com")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PersonName.of("   ")).isInstanceOf(DomainException.class);
    }
}
