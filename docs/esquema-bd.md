# Esquema de base de datos de CherryCore

Estado: **propuesta** (2026-11-05). Cuando una tabla se crea con Flyway, se marca con ✅ y su migración.

## Reglas comunes (todas las tablas)

| Columna | Tipo | Nota |
|---|---|---|
| `id` | `UUID` | UUID v7, lo genera la aplicación |
| `created_at` / `created_by` | `TIMESTAMPTZ` / `UUID` | `created_by` NULL = el sistema |
| `updated_at` / `updated_by` | `TIMESTAMPTZ` / `UUID` | |
| `deleted_at` / `deleted_by` | `TIMESTAMPTZ` / `UUID` | NULL = no borrado (borrado lógico) |
| `version` | `BIGINT` | Bloqueo optimista: evita que dos personas se pisen al editar |

- Los campos "únicos" (p. ej. email) solo son únicos **entre registros no borrados**.
- Los emails se guardan siempre en minúsculas.
- Los estados son `VARCHAR` + `CHECK`, y en Java un `enum`.
- Las tablas de **tokens y eventos** (de un solo uso o de solo lectura) no llevan `updated_*` ni `deleted_*`: se insertan y caducan.

## Diagrama

```
accounts ─┬─ 1:1 ─ customers ─ 1:N ─ customer_addresses
          ├─ 1:1 ─ staff_users ─ 1:N ─ memberships ─ N:1 ─ companies
          ├─ 1:N ─ mfa_factors                     └─ N:1 ─ company_roles ─ 1:N ─ company_role_permissions
          ├─ 1:N ─ federated_identities (Google, Apple)
          ├─ 1:N ─ one_time_tokens (verificar email, recuperar contraseña)
          └─ 1:N ─ auth_events (auditoría de seguridad)
companies ─ 1:N ─ staff_invitations
```

**Por qué `accounts`:** guarda lo de seguridad (email, contraseña, bloqueo, MFA) en un único sitio para clientes y backoffice. `customers` y `staff_users` guardan los datos de perfil. Así el MFA, el login social y los tokens se programan una sola vez.

## Cuentas y seguridad

### `accounts`
| Columna | Tipo | Nota |
|---|---|---|
| `type` | `VARCHAR` | `CUSTOMER` / `STAFF` |
| `email` | `VARCHAR(254)` | Único por `type` |
| `email_verified_at` | `TIMESTAMPTZ` | NULL = sin verificar → no puede entrar |
| `password_hash` | `VARCHAR` | Argon2id. NULL si solo usa login social |
| `status` | `VARCHAR` | `PENDING_VERIFICATION` / `ACTIVE` / `LOCKED` / `DISABLED` |
| `failed_login_attempts` | `INT` | Para el bloqueo por fuerza bruta |
| `locked_until` | `TIMESTAMPTZ` | Bloqueo temporal |
| `last_login_at` | `TIMESTAMPTZ` | |
| `platform_role` | `VARCHAR` | Solo `SUPER_ADMIN` o NULL. Solo en cuentas `STAFF` |

### `mfa_factors`
`account_id`, `type` (`TOTP` / `WEBAUTHN`), `secret` (**cifrado**), `confirmed_at`, `last_used_at`.

### `mfa_recovery_codes`
`account_id`, `code_hash`, `used_at`. Códigos de emergencia por si se pierde el móvil.

### `federated_identities`
`account_id`, `provider` (`GOOGLE` / `APPLE`), `provider_subject` (id del usuario en Google/Apple). Único: `provider + provider_subject`.

### `one_time_tokens`
`account_id`, `purpose` (`EMAIL_VERIFICATION` / `PASSWORD_RESET` / `EMAIL_CHANGE`), `token_hash` (**nunca el token en claro**), `expires_at`, `used_at`.

### `auth_events`
`account_id` (puede ser NULL si el email no existe), `type` (`LOGIN_OK`, `LOGIN_FAILED`, `PASSWORD_RESET`, `MFA_FAILED`…), `ip`, `user_agent`, `occurred_at`. Solo inserción.

## Clientes

### `customers` (id = `accounts.id`)
`first_name`, `last_name`, `phone` (opcional, formato E.164), `terms_accepted_at`, `terms_version`.

### `customer_addresses`
`customer_id`, `line1`, `line2`, `city`, `postal_code`, `region`, `country_code` (ISO 3166-1), `is_default`. Opcional; permite varias direcciones en el futuro.

## Empresas y backoffice

### `companies`
| Columna | Tipo | Nota |
|---|---|---|
| `legal_name` / `trade_name` | `VARCHAR` | Razón social / nombre comercial |
| `tax_id` | `VARCHAR` | CIF/NIF. Único |
| `email`, `phone` | `VARCHAR` | Contacto |
| `country_code` | `CHAR(2)` | |
| `status` | `VARCHAR` | `PENDING_APPROVAL` / `ACTIVE` / `SUSPENDED` / `REJECTED` |
| `reviewed_at` / `reviewed_by` | | Qué superadmin la aprobó o rechazó |
| `rejection_reason` | `TEXT` | Obligatorio si `REJECTED` |

### `staff_users` (id = `accounts.id`)
`first_name`, `last_name`, `phone`.

### `company_roles`
`company_id`, `code`, `name`, `is_system` (`true` en `OWNER` / `ADMIN` / `STAFF` → no se pueden borrar ni renombrar). Único: `company_id + code`.

### `company_role_permissions`
`role_id`, `permission` (código del permiso). ⚠️ Pendiente: de dónde sale el catálogo de permisos.

### `memberships`
`staff_user_id`, `company_id`, `role_id`, `status` (`ACTIVE` / `SUSPENDED`). Único: `staff_user_id + company_id`.

### `staff_invitations`
`company_id`, `email`, `role_id`, `token_hash`, `expires_at`, `accepted_at`, `invited_by`.

## Tablas de Spring Authorization Server

`oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent`. Esquema estándar de Spring; se adapta a Postgres y se crea con Flyway.
