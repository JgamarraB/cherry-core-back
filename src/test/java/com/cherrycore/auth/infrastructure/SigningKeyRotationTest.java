package com.cherrycore.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Rotación de la clave de firma: la anterior se sigue publicando (solo la
 * parte pública) para validar los tokens que aún no han caducado.
 */
class SigningKeyRotationTest {

    private static final String ISSUER = "https://auth.example.test";

    @TempDir
    Path keys;

    @Test
    void previousPublicKeysArePublishedAndStillValidateTheirTokens() throws Exception {
        RSAKey previous = new RSAKeyGenerator(2048).keyID("2025-old").generate();
        RSAKey current = new RSAKeyGenerator(2048).keyID("2026-new").generate();
        SigningKeyProvider provider = new SigningKeyProvider(new CherryCoreProperties.SigningKey("2026-new",
                pem("current-private.pem", "PRIVATE KEY", current.toPrivateKey().getEncoded()),
                pem("current-public.pem", "PUBLIC KEY", current.toPublicKey().getEncoded()), false,
                List.of(new CherryCoreProperties.PreviousSigningKey("2025-old",
                        pem("old-public.pem", "PUBLIC KEY", previous.toPublicKey().getEncoded())))));

        assertThat(provider.rsaKey().getKeyID()).isEqualTo("2026-new");
        assertThat(provider.rsaKey().isPrivate()).isTrue();
        assertThat(provider.verificationKeys().getKeys()).extracting(JWK::getKeyID)
                .containsExactly("2026-new", "2025-old");
        assertThat(provider.verificationKeys().getKeys()).noneMatch(JWK::isPrivate);

        JwtDecoder decoder = AuthorizationServerConfig.decoderFor(provider, ISSUER);
        assertThat(decoder.decode(token(previous)).getSubject()).isEqualTo("user");
        assertThat(decoder.decode(token(provider.rsaKey())).getSubject()).isEqualTo("user");

        RSAKey stranger = new RSAKeyGenerator(2048).keyID("2025-old").generate();
        assertThatThrownBy(() -> decoder.decode(token(stranger))).isInstanceOf(JwtException.class);
    }

    @Test
    void keyIdsMustBeUnique() throws Exception {
        RSAKey current = new RSAKeyGenerator(2048).keyID("same").generate();
        CherryCoreProperties.SigningKey config = new CherryCoreProperties.SigningKey("same",
                pem("p.pem", "PRIVATE KEY", current.toPrivateKey().getEncoded()),
                pem("q.pem", "PUBLIC KEY", current.toPublicKey().getEncoded()), false,
                List.of(new CherryCoreProperties.PreviousSigningKey("same",
                        pem("r.pem", "PUBLIC KEY", current.toPublicKey().getEncoded()))));

        assertThatThrownBy(() -> new SigningKeyProvider(config)).isInstanceOf(IllegalStateException.class);
    }

    private static String token(RSAKey key) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder().subject("user").issuer(ISSUER)
                        .expirationTime(Date.from(Instant.now().plusSeconds(60))).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private FileSystemResource pem(String name, String type, byte[] der) throws Exception {
        Path file = keys.resolve(name);
        Files.writeString(file, "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der) + "\n-----END " + type + "-----\n");
        return new FileSystemResource(file);
    }
}
