package com.cherrycore.auth.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

/**
 * Carga la clave RSA con la que se firman los JWT.
 *
 * <p>Producción: ficheros PEM (privada en PKCS#8, pública en X.509). Ver
 * docs/arranque.md para generarlos. Desarrollo/tests: clave efímera en memoria.</p>
 */
@Component
public class SigningKeyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SigningKeyProvider.class);
    private static final String LOG_EPHEMERAL_KEY =
            "Using an EPHEMERAL signing key: tokens become invalid on restart. Never use in production.";

    private static final int RSA_KEY_SIZE = 2048;

    private final RSAKey rsaKey;

    public SigningKeyProvider(CherryCoreProperties properties) {
        CherryCoreProperties.SigningKey config = properties.security().signingKey();
        this.rsaKey = config.generateEphemeral() ? generate(config.keyId()) : load(config);
    }

    public RSAKey rsaKey() {
        return rsaKey;
    }

    public RSAPublicKey publicKey() {
        try {
            return rsaKey.toRSAPublicKey();
        } catch (JOSEException invalidKey) {
            throw new IllegalStateException(invalidKey);
        }
    }

    private static RSAKey generate(String keyId) {
        LOGGER.warn(LOG_EPHEMERAL_KEY);
        try {
            return new RSAKeyGenerator(RSA_KEY_SIZE).keyID(keyId).generate();
        } catch (JOSEException generationFailure) {
            throw new IllegalStateException(generationFailure);
        }
    }

    private static RSAKey load(CherryCoreProperties.SigningKey config) {
        RSAPrivateKey privateKey = read(config.privateKeyLocation(), RsaKeyConverters.pkcs8()::convert);
        RSAPublicKey publicKey = read(config.publicKeyLocation(), RsaKeyConverters.x509()::convert);
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(config.keyId()).build();
    }

    private static <T> T read(Resource resource, java.util.function.Function<InputStream, T> converter) {
        try (InputStream input = resource.getInputStream()) {
            return converter.apply(input);
        } catch (IOException unreadable) {
            throw new IllegalStateException("Cannot read signing key: " + resource.getDescription(), unreadable);
        }
    }
}
