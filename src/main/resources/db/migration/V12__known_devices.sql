/*
 * Navegadores o dispositivos desde los que ha entrado cada cuenta, para avisar
 * por email cuando se entra desde uno nuevo. fingerprint_hash es un HMAC
 * (clave del servidor) de la cuenta y del id aleatorio de la cookie del
 * navegador: nunca se guarda la cookie ni la IP.
 */
CREATE TABLE known_devices (
    id                UUID         PRIMARY KEY,
    account_id        UUID         NOT NULL REFERENCES accounts (id),
    fingerprint_hash  CHAR(64)     NOT NULL,
    first_seen_at     TIMESTAMPTZ  NOT NULL,
    last_seen_at      TIMESTAMPTZ  NOT NULL,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        UUID,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_known_devices_fingerprint
    ON known_devices (account_id, fingerprint_hash)
    WHERE deleted_at IS NULL;
