package com.cherrycore.identity.infrastructure.security;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.cherrycore.identity.application.port.PasswordHasher;

@Component
class SpringPasswordHasher implements PasswordHasher {

    private static final int DUMMY_PASSWORD_BYTES = 32;
    private static final String EMPTY = "";

    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    SpringPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        byte[] randomBytes = new byte[DUMMY_PASSWORD_BYTES];
        new SecureRandom().nextBytes(randomBytes);
        this.dummyHash = passwordEncoder.encode(HexFormat.of().formatHex(randomBytes));
    }

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return rawPassword != null && passwordEncoder.matches(rawPassword, passwordHash);
    }

    @Override
    public void simulateMatch(String rawPassword) {
        passwordEncoder.matches(rawPassword == null ? EMPTY : rawPassword, dummyHash);
    }
}
