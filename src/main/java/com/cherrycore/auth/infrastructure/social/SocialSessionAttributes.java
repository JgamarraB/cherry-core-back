package com.cherrycore.auth.infrastructure.social;

/**
 * Datos del login social que se guardan en la sesión entre redirecciones.
 */
final class SocialSessionAttributes {

    /** CUSTOMER o STAFF: qué tipo de cuenta busca el usuario. */
    static final String ACCOUNT_TYPE = "cherrycore.social.accountType";
    /** Perfil del proveedor de un cliente nuevo, hasta que acepta los términos. */
    static final String PENDING_SIGNUP = "cherrycore.social.pendingSignup";

    private SocialSessionAttributes() {
    }
}
