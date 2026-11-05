/*
 * Segundo factor (MFA): app autenticadora (TOTP) y códigos de recuperación.
 */

CREATE TABLE mfa_factors (
    id                UUID         PRIMARY KEY,
    account_id        UUID         NOT NULL REFERENCES accounts (id),
    type              VARCHAR(20)  NOT NULL,
    -- Secreto cifrado con AES-GCM (clave en variable de entorno). Nunca en claro.
    secret_encrypted  VARCHAR(500) NOT NULL,
    confirmed_at      TIMESTAMPTZ,
    -- Último intervalo de 30 s usado: impide reutilizar el mismo código (replay).
    last_used_step    BIGINT,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        UUID,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_mfa_factors_type CHECK (type IN ('TOTP'))
);

-- Una cuenta tiene como mucho un factor TOTP vigente.
CREATE UNIQUE INDEX uq_mfa_factors_account_type ON mfa_factors (account_id, type) WHERE deleted_at IS NULL;


CREATE TABLE mfa_recovery_codes (
    id          UUID        PRIMARY KEY,
    account_id  UUID        NOT NULL REFERENCES accounts (id),
    code_hash   VARCHAR(64) NOT NULL,
    used_at     TIMESTAMPTZ,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    deleted_by  UUID,
    version     BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_mfa_recovery_codes_account ON mfa_recovery_codes (account_id) WHERE deleted_at IS NULL;
