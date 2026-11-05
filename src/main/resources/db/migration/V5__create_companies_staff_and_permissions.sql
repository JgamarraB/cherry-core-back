/*
 * Empresas (tenants), personal de backoffice, apps con su catálogo de
 * permisos y roles por empresa. Reglas comunes: ver docs/esquema-bd.md.
 */

-- Apps de la plataforma (CherryCore, Lirgar...). Cada una registra sus permisos.
CREATE TABLE applications (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL,

    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    deleted_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_applications_code CHECK (code ~ '^[a-z][a-z0-9-]{1,49}$'),
    CONSTRAINT chk_applications_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX uq_applications_code ON applications (code) WHERE deleted_at IS NULL;


-- Catálogo de permisos de cada app (lo sincroniza la propia app).
CREATE TABLE application_permissions (
    id              UUID         PRIMARY KEY,
    application_id  UUID         NOT NULL REFERENCES applications (id),
    code            VARCHAR(100) NOT NULL,
    description     VARCHAR(255),

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    version         BIGINT       NOT NULL DEFAULT 0,

    -- p. ej. vehicles.manage, reservations.view
    CONSTRAINT chk_application_permissions_code CHECK (code ~ '^[a-z][a-z0-9_.-]{1,99}$')
);

CREATE UNIQUE INDEX uq_application_permissions_code
    ON application_permissions (application_id, code)
    WHERE deleted_at IS NULL;


CREATE TABLE companies (
    id                UUID         PRIMARY KEY,
    legal_name        VARCHAR(200) NOT NULL,
    trade_name        VARCHAR(100) NOT NULL,
    tax_id            VARCHAR(30)  NOT NULL,
    email             VARCHAR(254),
    phone             VARCHAR(16),
    country_code      VARCHAR(2)   NOT NULL,
    status            VARCHAR(30)  NOT NULL,
    reviewed_at       TIMESTAMPTZ,
    reviewed_by       UUID,
    rejection_reason  VARCHAR(500),
    logo_key          VARCHAR(300),

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        UUID,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    deleted_by        UUID,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_companies_status
        CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED', 'REJECTED')),
    CONSTRAINT chk_companies_country CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT chk_companies_phone CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{6,14}$'),
    CONSTRAINT chk_companies_email CHECK (email IS NULL OR email = LOWER(email)),
    CONSTRAINT chk_companies_rejection
        CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);

CREATE UNIQUE INDEX uq_companies_tax_id ON companies (country_code, tax_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_companies_status ON companies (status) WHERE deleted_at IS NULL;


-- Qué apps tiene activadas cada empresa (más adelante lo gestionará la suscripción).
CREATE TABLE company_applications (
    id              UUID         PRIMARY KEY,
    company_id      UUID         NOT NULL REFERENCES companies (id),
    application_id  UUID         NOT NULL REFERENCES applications (id),
    status          VARCHAR(20)  NOT NULL,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_company_applications_status CHECK (status IN ('ENABLED', 'DISABLED'))
);

CREATE UNIQUE INDEX uq_company_applications
    ON company_applications (company_id, application_id)
    WHERE deleted_at IS NULL;


CREATE TABLE staff_users (
    id          UUID         PRIMARY KEY REFERENCES accounts (id),
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    phone       VARCHAR(16),

    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    deleted_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_staff_users_phone CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{6,14}$')
);


CREATE TABLE company_roles (
    id          UUID         PRIMARY KEY,
    company_id  UUID         NOT NULL REFERENCES companies (id),
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    -- OWNER, ADMIN y STAFF: no se pueden borrar ni renombrar.
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    deleted_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_company_roles_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,49}$')
);

CREATE UNIQUE INDEX uq_company_roles_code ON company_roles (company_id, code) WHERE deleted_at IS NULL;


CREATE TABLE company_role_permissions (
    id             UUID        PRIMARY KEY,
    role_id        UUID        NOT NULL REFERENCES company_roles (id),
    permission_id  UUID        NOT NULL REFERENCES application_permissions (id),

    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     UUID,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     UUID,
    deleted_at     TIMESTAMPTZ,
    deleted_by     UUID,
    version        BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_company_role_permissions
    ON company_role_permissions (role_id, permission_id)
    WHERE deleted_at IS NULL;


CREATE TABLE memberships (
    id             UUID        PRIMARY KEY,
    staff_user_id  UUID        NOT NULL REFERENCES staff_users (id),
    company_id     UUID        NOT NULL REFERENCES companies (id),
    role_id        UUID        NOT NULL REFERENCES company_roles (id),
    status         VARCHAR(20) NOT NULL,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     UUID,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     UUID,
    deleted_at     TIMESTAMPTZ,
    deleted_by     UUID,
    version        BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT chk_memberships_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE UNIQUE INDEX uq_memberships ON memberships (staff_user_id, company_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_memberships_company ON memberships (company_id) WHERE deleted_at IS NULL;


CREATE TABLE staff_invitations (
    id           UUID         PRIMARY KEY,
    company_id   UUID         NOT NULL REFERENCES companies (id),
    email        VARCHAR(254) NOT NULL,
    role_id      UUID         NOT NULL REFERENCES company_roles (id),
    token_hash   VARCHAR(64)  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    accepted_at  TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   UUID,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   UUID,
    deleted_at   TIMESTAMPTZ,
    deleted_by   UUID,
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_staff_invitations_token UNIQUE (token_hash),
    CONSTRAINT chk_staff_invitations_email CHECK (email = LOWER(email))
);

CREATE INDEX idx_staff_invitations_company ON staff_invitations (company_id) WHERE deleted_at IS NULL;
