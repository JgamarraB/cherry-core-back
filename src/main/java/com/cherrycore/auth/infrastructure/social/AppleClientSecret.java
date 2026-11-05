package com.cherrycore.auth.infrastructure.social;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Apple no usa un secreto fijo: el "client secret" es un JWT firmado (ES256)
 * con la clave .p8 de la cuenta de desarrollador. Se regenera antes de caducar.
 */
class AppleClientSecret {

    private static final String AUDIENCE = "https://appleid.apple.com";
    private static final Duration VALIDITY = Duration.ofDays(30);
    private static final Duration RENEW_BEFORE = Duration.ofDays(1);
    private static final String EC = "EC";
    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";
    private static final String WHITESPACE = "\\s";
    private static final String NOTHING = "";

    private final String teamId;
    private final String keyId;
    private final String clientId;
    private final ECPrivateKey privateKey;
    private final Clock clock;
    private String cached;
    private Instant cachedUntil = Instant.MIN;

    AppleClientSecret(CherryCoreProperties.Apple apple, String clientId, Clock clock) {
        this.teamId = apple.teamId();
        this.keyId = apple.keyId();
        this.clientId = clientId;
        this.privateKey = load(apple);
        this.clock = clock;
    }

    synchronized String current() {
        Instant now = clock.instant();
        if (cached == null || now.isAfter(cachedUntil.minus(RENEW_BEFORE))) {
            cachedUntil = now.plus(VALIDITY);
            cached = sign(now, cachedUntil);
        }
        return cached;
    }

    private String sign(Instant issuedAt, Instant expiresAt) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(),
                    new JWTClaimsSet.Builder()
                            .issuer(teamId)
                            .subject(clientId)
                            .audience(AUDIENCE)
                            .issueTime(Date.from(issuedAt))
                            .expirationTime(Date.from(expiresAt))
                            .build());
            jwt.sign(new ECDSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException signingFailure) {
            throw new IllegalStateException(signingFailure);
        }
    }

    private static ECPrivateKey load(CherryCoreProperties.Apple apple) {
        try (InputStream input = apple.privateKeyLocation().getInputStream()) {
            String pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .replace(PEM_HEADER, NOTHING).replace(PEM_FOOTER, NOTHING).replaceAll(WHITESPACE, NOTHING);
            return (ECPrivateKey) KeyFactory.getInstance(EC)
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (IOException | GeneralSecurityException invalidKey) {
            throw new IllegalStateException("Cannot load Apple private key", invalidKey);
        }
    }
}
