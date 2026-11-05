# Esquema de base de datos de CherryCore

Estado (2026-11-05): todas las tablas creadas con Flyway (se indica la migración).

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
          ├─ 1:N ─ mfa_factors / mfa_recovery_codes └─ N:1 ─ company_roles ─ 1:N ─ company_role_permissions
          ├─ 1:N ─ federated_identities (Google, Apple)
          ├─ 1:N ─ one_time_tokens (verificar email, recuperar contraseña)
          └─ 1:N ─ auth_events (auditoría de seguridad)
companies ─ 1:N ─ staff_invitations
companies ─ N:M ─ applications (company_applications)
applications ─ 1:N ─ application_permissions ─ N:M ─ company_roles
```

**Por qué `accounts`:** guarda lo de seguridad (email, contraseña, bloqueo, MFA) en un único sitio para clientes y backoffice. `customers` y `staff_users` guardan los datos de perfil. Así el MFA, el login social y los tokens se programan una sola vez.

## Cuentas y seguridad

### `accounts` ✅ V1
| Columna | Tipo | Nota |
|---|---|---|
| `type` | `VARCHAR` | `CUSTOMER` / `STAFF` |
| `email` | `VARCHAR(254)` | Único por `type` |
| `email_verified_at` | `TIMESTAMPTZ` | NULL = sin verificar → no puede entrar |
| `password_hash` | `VARCHAR` | Argon2id. NULL si solo usa login social |
| `status` | `VARCHAR` | `PENDING_VERIFICATION` / `ACTIVE` / `DISABLED` (el bloqueo es temporal: `locked_until`) |
| `failed_login_attempts` | `INT` | Para el bloqueo por fuerza bruta |
| `locked_until` | `TIMESTAMPTZ` | Bloqueo temporal |
| `last_login_at` | `TIMESTAMPTZ` | |
| `platform_role` | `VARCHAR` | Solo `SUPER_ADMIN` o NULL. Solo en cuentas `STAFF` |
| `locale` | `VARCHAR` | Idioma de la cuenta (`es`, `en`): idioma de los emails |

### `mfa_factors` ✅ V6
`account_id`, `type` (`TOTP`), `secret_encrypted` (**AES-256-GCM**), `confirmed_at`, `last_used_step` (impide reutilizar un código).

### `mfa_recovery_codes` ✅ V6
`account_id`, `code_hash`, `used_at`. Códigos de emergencia por si se pierde el móvil.

### `federated_identities` ✅ V8
`account_id`, `provider` (`GOOGLE` / `APPLE`), `provider_subject` (id del usuario en Google/Apple), `email_at_link`. Único: `provider + provider_subject`.

### `one_time_tokens` ✅ V2
`account_id`, `purpose` (`EMAIL_VERIFICATION` / `PASSWORD_RESET` / `EMAIL_CHANGE`), `payload` (email nuevo, V7), `token_hash` (**nunca el token en claro**), `expires_at`, `used_at`.

### `auth_events` ✅ V2
`account_id` (puede ser NULL si el email no existe), `type` (`LOGIN_OK`, `LOGIN_FAILED`, `PASSWORD_RESET`, `MFA_FAILED`…), `ip`, `user_agent`, `occurred_at`, `details` (JSONB con claves permitidas: actor, cuenta afectada, empresa, app, rol, motivo… nunca secretos; V11). Solo inserción.

## Clientes

### `customers` ✅ V1 (id = `accounts.id`)
`first_name`, `last_name`, `phone` (opcional, formato E.164), `terms_accepted_at`, `terms_version`.

### `customer_addresses` ✅ V1
`customer_id`, `line1`, `line2`, `city`, `postal_code`, `region`, `country_code` (ISO 3166-1), `is_default`. Opcional; permite varias direcciones en el futuro.

## Empresas y backoffice

### `companies` ✅ V5
| Columna | Tipo | Nota |
|---|---|---|
| `legal_name` / `trade_name` | `VARCHAR` | Razón social / nombre comercial |
| `tax_id` | `VARCHAR` | CIF/NIF normalizado. Único por país |
| `email`, `phone` | `VARCHAR` | Contacto |
| `country_code` | `CHAR(2)` | |
| `status` | `VARCHAR` | `PENDING_APPROVAL` / `ACTIVE` / `SUSPENDED` / `REJECTED` |
| `reviewed_at` / `reviewed_by` | | Qué superadmin la aprobó o rechazó |
| `rejection_reason` | `VARCHAR` | Obligatorio si `REJECTED` |
| `logo_key` | `VARCHAR` | Ruta del logo en el almacenamiento (o NULL) |

### `staff_users` ✅ V5 (id = `accounts.id`)
`first_name`, `last_name`, `phone`.

### `company_roles` ✅ V5
`company_id`, `code`, `name`, `is_system` (`true` en `OWNER` / `ADMIN` / `STAFF` → no se pueden borrar ni renombrar). Único: `company_id + code`.

### `company_role_permissions` ✅ V5
`role_id`, `permission_id` (→ `application_permissions`). OWNER no tiene filas: tiene todos.

### `memberships` ✅ V5
`staff_user_id`, `company_id`, `role_id`, `status` (`ACTIVE` / `SUSPENDED`). Único: `staff_user_id + company_id`.

### `staff_invitations` ✅ V5
`company_id`, `email`, `role_id`, `token_hash`, `expires_at`, `accepted_at`, `revoked_at`. Quien invita queda en `created_by`.

## Apps y permisos

### `applications` ✅ V5
`code` (`cherrycore`, `lirgar`...), `name`, `status` (`ACTIVE` / `DISABLED`), `logo_key` (logo público de la app, V10).

### `application_permissions` ✅ V5
`application_id`, `code` (`vehicles.manage`...), `description`. Lo sincroniza cada app.

### `company_applications` ✅ V5
`company_id`, `application_id`, `status` (`ENABLED` / `DISABLED`). Qué apps puede usar cada empresa (futuro: suscripción).

## Tablas de framework ✅ V3, V4

No siguen nuestras reglas de auditoría y borrado lógico (son de Spring y no se tocan a mano).

- **V3, Spring Authorization Server**: `oauth2_registered_client` (apps cliente), `oauth2_authorization` (códigos y tokens emitidos), `oauth2_authorization_consent`.
- **V4, Spring Session**: `spring_session`, `spring_session_attributes` (sesiones de login).
