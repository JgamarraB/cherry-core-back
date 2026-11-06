package com.cherrycore.identity.application;

/**
 * Datos de la petición que se guardan en la auditoría de seguridad.
 */
public record RequestContext(String ip, String userAgent) {

    public static final int MAX_USER_AGENT_LENGTH = 512;

    /** Operación sin petición HTTP (tareas internas, arranque). */
    public static final RequestContext NONE = new RequestContext(null, null);

    public RequestContext {
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        }
    }
}
