/*
 * Cuentas (seguridad del login) y perfil de cliente final.
 * Reglas comunes: ids UUID v7 generados por la aplicación, auditoría,
 * borrado lógico y bloqueo optimista (version). Ver docs/esquema-bd.md.
 */

CREATE TABLE accounts (
    id                    UUID         PRIMARY KEY,
    type                  VARCHAR(20)  NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    email_verified_at     TIMESTAMPTZ,
    password_hash         VARCHAR(255),
    status                VARCHAR(30)  NOT NULL,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    platform_role         VARCHAR(30),
    locale                VARCHAR(10)  NOT NULL,

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            UUID,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    deleted_by            UUID,
    version               BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_accounts_type
        CHECK (type IN ('CUSTOMER', 'STAFF')),
    CONSTRAINT chk_accounts_status
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED')),
    CONSTRAINT chk_accounts_email_lowercase
        CHECK (email = LOWER(email)),
    CONSTRAINT chk_accounts_failed_attempts
        CHECK (failed_login_attempts >= 0),
    -- Solo el personal de backoffice puede tener rol de plataforma.
    CONSTRAINT chk_accounts_platform_role
        CHECK (platform_role IS NULL OR (type = 'STAFF' AND platform_role IN ('SUPER_ADMIN'))),
    -- Una cuenta activa siempre tiene el email verificado.
    CONSTRAINT chk_accounts_active_is_verified
        CHECK (status <> 'ACTIVE' OR email_verified_at IS NOT NULL)
);

-- El email es único por tipo de cuenta, solo entre cuentas no borradas.
CREATE UNIQUE INDEX uq_accounts_type_email
    ON accounts (type, email)
    WHERE deleted_at IS NULL;


CREATE TABLE customers (
    id                UUID         PRIMARY KEY REFERENCES accounts (id),
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    phone             VARCHAR(16),
    terms_accepted_at TIMESTAMPTZ  NOT NULL,
    terms_version     VARCHAR(30)  NOT NULL,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        UUID,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,

    -- Formato internacional E.164: +34600111222
    CONSTRAINT chk_customers_phone
        CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{6,14}$')
);


CREATE TABLE customer_addresses (
    id           UUID         PRIMARY KEY,
    customer_id  UUID         NOT NULL REFERENCES customers (id),
    line1        VARCHAR(200) NOT NULL,
    line2        VARCHAR(200),
    city         VARCHAR(100) NOT NULL,
    postal_code  VARCHAR(20)  NOT NULL,
    region       VARCHAR(100),
    country_code VARCHAR(2)   NOT NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   UUID,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   UUID,
    deleted_at   TIMESTAMPTZ,
    deleted_by   UUID,
    version      BIGINT       NOT NULL DEFAULT 0,

    -- ISO 3166-1 alpha-2 en mayúsculas: ES, FR...
    CONSTRAINT chk_customer_addresses_country
        CHECK (country_code ~ '^[A-Z]{2}$')
);

CREATE INDEX idx_customer_addresses_customer
    ON customer_addresses (customer_id)
    WHERE deleted_at IS NULL;

-- Como mucho una dirección por defecto por cliente.
CREATE UNIQUE INDEX uq_customer_addresses_default
    ON customer_addresses (customer_id)
    WHERE is_default AND deleted_at IS NULL;
