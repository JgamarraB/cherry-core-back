/*
 * Contexto de cada evento de auditoría: quién lo hizo (acciones de admin),
 * sobre qué empresa, cuenta, app o rol... Pares clave/valor de texto.
 * Nunca contraseñas, secretos ni tokens. NULL si el evento no lleva contexto.
 */
ALTER TABLE auth_events ADD COLUMN details JSONB;

ALTER TABLE auth_events
    ADD CONSTRAINT chk_auth_events_details CHECK (details IS NULL OR jsonb_typeof(details) = 'object');
