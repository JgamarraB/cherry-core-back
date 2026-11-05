/*
 * Tokens de un solo uso (verificar email, recuperar contraseña) y
 * auditoría de seguridad. Son tablas de "solo insertar": no llevan
 * updated_* ni borrado lógico.
 */

CREATE TABLE one_time_tokens (
    id          UUID        PRIMARY KEY,
    account_id  UUID        NOT NULL REFERENCES accounts (id),
    purpose     VARCHAR(30) NOT NULL,
    -- SHA-256 en hexadecimal. El token en claro solo viaja en el email.
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_one_time_tokens_hash UNIQUE (token_hash),
    CONSTRAINT chk_one_time_tokens_purpose
        CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);

CREATE INDEX idx_one_time_tokens_account_purpose
    ON one_time_tokens (account_id, purpose)
    WHERE used_at IS NULL;


CREATE TABLE auth_events (
    id          UUID         PRIMARY KEY,
    -- NULL cuando el email usado no corresponde a ninguna cuenta.
    account_id  UUID         REFERENCES accounts (id),
    type        VARCHAR(40)  NOT NULL,
    ip          VARCHAR(45),
    user_agent  VARCHAR(512),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auth_events_account_time
    ON auth_events (account_id, occurred_at DESC);

CREATE INDEX idx_auth_events_type_time
    ON auth_events (type, occurred_at DESC);
