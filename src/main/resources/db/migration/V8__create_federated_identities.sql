/*
 * Login social: cuentas vinculadas a Google / Apple.
 * provider_subject es el id del usuario en el proveedor ("sub"): no cambia
 * aunque el usuario cambie de email.
 */
CREATE TABLE federated_identities (
    id                UUID         PRIMARY KEY,
    account_id        UUID         NOT NULL REFERENCES accounts (id),
    provider          VARCHAR(20)  NOT NULL,
    provider_subject  VARCHAR(255) NOT NULL,
    email_at_link     VARCHAR(254),

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        UUID,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_federated_identities_provider CHECK (provider IN ('GOOGLE', 'APPLE'))
);

CREATE UNIQUE INDEX uq_federated_identities_subject
    ON federated_identities (provider, provider_subject)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_federated_identities_account ON federated_identities (account_id) WHERE deleted_at IS NULL;
