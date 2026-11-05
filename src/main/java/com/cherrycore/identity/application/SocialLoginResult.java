package com.cherrycore.identity.application;

import com.cherrycore.identity.domain.SocialProfile;

/**
 * Resultado de volver de Google/Apple.
 */
public sealed interface SocialLoginResult {

    /** Cuenta encontrada (o vinculada): continuar el login. */
    record SignedIn(AuthenticatedAccount account) implements SocialLoginResult {
    }

    /** Cliente nuevo: debe aceptar los términos para crear la cuenta. */
    record SignupRequired(SocialProfile profile) implements SocialLoginResult {
    }
}
