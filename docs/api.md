# API de CherryCore para su frontend

Referencia para el frontend propio de CherryCore: login, registro, "mi cuenta", gestión de empresa y panel de superadmin. Cómo se conecta una app externa (Lirgar…): [integracion-apps.md](integracion-apps.md).

## Reglas comunes

- **Mismo dominio** que la API (p. ej. `auth.tudominio.com` sirve el frontend y pasa `/api`, `/oauth2`, `/login/oauth2` y `/.well-known` a CherryCore).
- **Cookies**: `credentials: 'include'` (Angular: `withCredentials: true`).
- **CSRF**: al arrancar, `GET /api/auth/csrf`. En cada POST/PUT/DELETE, enviar el valor de la cookie `XSRF-TOKEN` en la cabecera `X-XSRF-TOKEN` (Angular lo hace solo).
- **Idioma**: cabecera `Accept-Language` (`es`, `en`). Fija el idioma de los emails.
- **Respuesta**: `{ "ok": true, "data": … }` o `{ "ok": false, "error": { "code", "description", "fields"? } }`. Se traduce por `code`.
- **Listados**: `?page=0&size=20` (máx. 100) → `data: { items, page, size, totalItems, totalPages }`.
- Errores comunes: `VALIDATION_ERROR` (400, con `fields`), `MALFORMED_REQUEST` (400), `AUTHENTICATION_REQUIRED` (401), `ACCESS_DENIED` (403), `CSRF_TOKEN_INVALID` (403), `TOO_MANY_REQUESTS` (429 + `Retry-After`), `CONCURRENT_MODIFICATION` (409), `INTERNAL_ERROR` (500).

## 1. Login (`/api/auth`)

Si una app manda al usuario a `/oauth2/authorize` sin sesión, CherryCore le lleva a `/login?client_id=<app>` (solo si la app está registrada). Con `client_id` el login ofrece cliente y empresa; sin él, es solo acceso de empresa (los clientes son de cada app).

El login va por pasos. Cada respuesta trae `data.status` con el siguiente paso:

| `status` | Qué muestra el frontend |
|---|---|
| `AUTHENTICATED` | Entró. Si `redirectUrl` ≠ null → `window.location.href = redirectUrl` (volver a la app). |
| `MFA_REQUIRED` | Pedir el código de 6 dígitos (o un código de recuperación). |
| `MFA_SETUP_REQUIRED` | Primer login de backoffice: configurar la app autenticadora. |
| `TENANT_SELECTION_REQUIRED` | Elegir empresa (`GET /staff/tenants`). |

| Método y ruta | Body | Respuesta |
|---|---|---|
| `GET /csrf` | — | cookie `XSRF-TOKEN` |
| `GET /session` | — | `{authenticated, accountId, accountType, superAdmin, activeCompanyId, pendingStep}` |
| `POST /customers/login` · `POST /staff/login` | `{email, password}` | `{status, redirectUrl}` |
| `POST /mfa/verify` | `{code}` | `{status, redirectUrl}` |
| `POST /mfa/setup` | — | `{secret, otpauthUri}` → pintar `otpauthUri` como **QR** |
| `POST /mfa/setup/confirm` | `{code}` | `{recoveryCodes[10], login:{status, redirectUrl}}` → **mostrar los códigos una vez** |
| `GET /staff/tenants` | — | `{activeCompanyId, tenants:[{companyId, name, logoUrl, roleCode, roleName}]}` |
| `POST /staff/tenant` | `{companyId}` | `{status, redirectUrl}` (también sirve para cambiar de empresa) |
| `POST /logout` | — | — |
| `GET /apps/{clientId}` | — | `{name, logoUrl}` de la app que pide el login (público; 404 `CLIENT_NOT_FOUND` si no existe, no tiene app o está desactivada; limitado por IP) |

Errores: `INVALID_CREDENTIALS` (401: email, contraseña, bloqueo o cuenta desactivada; nunca se distingue), `EMAIL_NOT_VERIFIED` (403), `INVALID_MFA_CODE` (401), `NO_PENDING_LOGIN` (401: volver a pedir la contraseña).

## 2. Registro y recuperación (`/api/auth`)

| Método y ruta | Body | OK |
|---|---|---|
| `POST /customers/register` | `{email, password, firstName, lastName, phone?, acceptTerms}` | **202** siempre |
| `POST /companies/register` | `{company:{legalName, tradeName, taxId, email?, phone?, countryCode}, owner:{email, password, firstName, lastName, phone?}, acceptTerms}` | **202** siempre |
| `POST /email-verification/confirm` | `{token}` | 200 |
| `POST /customers/email-verification/resend` · `POST /staff/email-verification/resend` | `{email}` | **202** siempre |
| `POST /customers/password/forgot` · `POST /staff/password/forgot` | `{email}` | **202** siempre |
| `POST /password/reset` | `{token, newPassword}` | 200 (cierra todas las sesiones) |
| `POST /email-change/confirm` | `{token}` | 200 |
| `POST /invitations/lookup` | `{token}` | `{companyName, companyLogoUrl, email, roleName, accountExists}` |
| `POST /invitations/accept` | con sesión: `{token}` · sin cuenta: `{token, firstName, lastName, phone?, password}` | 200 |

Pantallas a las que llevan los emails (`?token=…`): `/verify-email`, `/reset-password`, `/invitation`, `/confirm-email-change`.

Errores de datos: `INVALID_EMAIL`, `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG`, `PASSWORD_CONTAINS_EMAIL`, `TERMS_NOT_ACCEPTED`, `INVALID_NAME`, `INVALID_PHONE`, `INVALID_COUNTRY_CODE`, `INVALID_TAX_ID`, `TAX_ID_ALREADY_REGISTERED`, `INVALID_OR_EXPIRED_TOKEN`, `INVALID_OR_EXPIRED_INVITATION`, `INVITATION_FOR_ANOTHER_ACCOUNT`.

**Contraseña**: 10–128 caracteres, sin contener el email. Sin reglas de mayúsculas/símbolos.

## 3. Login social (`/api/auth/social`)

1. `GET /providers` → `["google", "apple"]` (los configurados).
2. Navegar (no fetch) a `/{provider}/start?type=CUSTOMER|STAFF`.
3. CherryCore vuelve al frontend:
   - a la app (si venía de una) o a `/login?status=AUTHENTICATED`;
   - a `/login?status=MFA_REQUIRED|MFA_SETUP_REQUIRED|TENANT_SELECTION_REQUIRED` → seguir como en el login normal;
   - a `/register/complete` (cliente nuevo) → `GET /pending` (`{provider, email, firstName, lastName}` para rellenar) y `POST /complete` `{firstName, lastName, phone?, acceptTerms}` → `{status, redirectUrl}`;
   - a `/login?error=CODIGO` (`SOCIAL_EMAIL_NOT_VERIFIED`, `SOCIAL_ACCOUNT_NOT_FOUND`, `SOCIAL_LOGIN_FAILED`).

## 4. Mi cuenta (`/api/account`, con sesión)

| Método y ruta | Body | Respuesta |
|---|---|---|
| `GET /` | — | `{id, type, email, emailVerified, hasPassword, locale, firstName, lastName, phone, superAdmin, mfa:{enabled, required, recoveryCodesLeft}}` |
| `PUT /profile` | `{firstName, lastName, phone?, locale?}` | igual que `GET /` |
| `PUT /password` | `{currentPassword, newPassword}` | — (cierra el resto de sesiones) |
| `POST /email` | `{currentPassword, newEmail}` | 202 (enlace al email nuevo) |
| `GET /sessions` | — | `[{id, createdAt, lastActiveAt, ip, userAgent, current}]` |
| `DELETE /sessions/{id}` | — | — |
| `GET /mfa` | — | `{enabled, required, recoveryCodesLeft}` |
| `POST /mfa/setup` | `{currentPassword}` (`{}` si la cuenta no tiene contraseña) | `{secret, otpauthUri}`. Errores: `WRONG_CURRENT_PASSWORD` (cuenta para el bloqueo), `REAUTHENTICATION_REQUIRED` |
| `POST /mfa/setup/confirm` | `{code}` | `{recoveryCodes[10]}` |
| `POST /mfa/disable` | `{code}` | — (solo clientes) |
| `POST /mfa/recovery-codes` | `{code}` | `{recoveryCodes}` nuevos |
| `POST /delete` | `{currentPassword, mfaCode?}` | — (borra y cierra sesión) |
| `GET /companies` | — | mis empresas en cualquier estado: `[{company, roleCode, roleName, membershipStatus}]` |
| `POST /companies` | datos de empresa | registra otra empresa (pendiente de aprobación) |
| `POST /companies/{companyId}/leave` | — | salir de una empresa (backoffice); si era la activa, se quita de la sesión |
| `GET /export` | — | descarga JSON con mis datos (RGPD): `{exportedAt, account, customer, staff, sessions[], securityEvents[], federatedIdentities[]}`. Sin secretos; limitado por IP y auditado |

`hasPassword: false` (alta con login social): no se pide `currentPassword`; basta con haber entrado hace poco (si no, `REAUTHENTICATION_REQUIRED`).

Errores: `WRONG_CURRENT_PASSWORD`, `EMAIL_UNCHANGED`, `EMAIL_ALREADY_IN_USE`, `MFA_REQUIRED_FOR_STAFF`, `MFA_ALREADY_ENABLED`, `SESSION_NOT_FOUND`, `SOLE_OWNER_CANNOT_LEAVE`.

## 5. Mi empresa (`/api/company`, backoffice con empresa elegida)

Solo se entra en empresas **activas** (las pendientes o rechazadas se ven en Mi cuenta → `GET /api/account/companies`). Cada acción comprueba un permiso de CherryCore: `company.profile.manage`, `company.members.view`, `company.members.manage`, `company.roles.manage`, `company.applications.view`. Sin él → `COMPANY_PERMISSION_DENIED` (403).

| Método y ruta | Body | Permiso |
|---|---|---|
| `GET /me` | — | miembro → `{companyId, membershipId, roleCode, roleName, owner, permissions[]}` (para no enseñar lo que no puede hacer) |
| `GET /` · `PUT /` | datos de empresa | miembro · `profile.manage` |
| `PUT /logo` (multipart, campo `file`, ≤ 2 MB; más grande → 413 `FILE_TOO_LARGE`) · `DELETE /logo` | — | `profile.manage` |
| `GET /members` | — | `members.view` |
| `PUT /members/{id}` | `{roleId}` | `members.manage` (+ OWNER para dar/quitar OWNER) |
| `POST /members/{id}/suspend` · `/reactivate` · `DELETE /members/{id}` | — | `members.manage` |
| `GET /roles` · `GET /permissions` | — | `members.view` |
| `POST /roles` | `{code, name, permissionIds[]}` | `roles.manage` |
| `PUT /roles/{id}` | `{name?, permissionIds[]}` | `roles.manage` |
| `DELETE /roles/{id}` | — | `roles.manage` |
| `GET /invitations` · `POST /invitations` `{email, roleId}` · `DELETE /invitations/{id}` | | `members.view` / `members.manage` |
| `POST /members/{id}/transfer-ownership` | — | solo OWNER → `MemberView` del nuevo dueño; quien la cede pasa a ADMIN y el nuevo dueño recibe un email |
| `GET /applications` | — | `applications.view` |

Errores: `NO_COMPANY_SELECTED`, `LAST_OWNER`, `OWNER_ROLE_RESERVED`, `SYSTEM_ROLE_IMMUTABLE`, `OWNER_PERMISSIONS_FIXED`, `ROLE_IN_USE`, `ROLE_CODE_ALREADY_EXISTS`, `PERMISSION_NOT_AVAILABLE`, `ALREADY_MEMBER`, `INVALID_OWNERSHIP_TRANSFER`, `ROLE_ABOVE_OWN_PERMISSIONS` (403: quien no es OWNER intenta dar permisos que no tiene; al cambiar rol, invitar o crear/editar roles), `LOGO_INVALID_FORMAT`, `LOGO_TOO_LARGE`.

## 6. Panel de superadmin (`/api/admin`, solo SUPER_ADMIN)

| Ruta | Qué hace |
|---|---|
| `GET /dashboard` | cifras: cuentas, empresas por estado, logins/fallos/bloqueos/registros últimas 24 h |
| `GET /companies?status=&search=` · `GET /companies/{id}` · `/members` · `/applications` | empresas |
| `POST /companies/{id}/approve` · `/reject` `{reason}` · `/suspend` · `/reactivate` | revisión |
| `PUT /companies/{id}/applications/{appId}` `{enabled}` | activar apps a mano |
| `GET /accounts?type=&status=&search=` · `GET /accounts/{id}` | cuentas (detalle con MFA, empresas y últimos eventos) |
| `POST /accounts/{id}/disable` · `/enable` · `/unlock` · `/mfa/reset` · `/password-reset` | acciones |
| `PUT /accounts/{id}/super-admin` `{enabled}` | dar/quitar superadmin |
| `GET /applications` · `POST /applications` `{code, name}` · `PUT /applications/{id}` `{name, active}` · `GET /applications/{id}/permissions` · `PUT`/`DELETE /applications/{id}/logo` (multipart `file`, ≤ 2 MB) | apps del catálogo (`ApplicationView` incluye `logoUrl`) |
| `GET /clients` · `POST /clients` · `PUT /clients/{clientId}` · `POST /clients/{clientId}/secret` · `DELETE /clients/{clientId}` | apps cliente OAuth (el secreto solo aparece al crear o rotar) |
| `GET /audit-events?accountId=&type=&from=&to=` | auditoría de seguridad (cada evento con `details`, `actorEmail`, `targetEmail`) |

Errores: `CANNOT_MODIFY_OWN_ACCOUNT`, `LAST_SUPER_ADMIN`, `NOT_A_STAFF_ACCOUNT`, `INVALID_STATUS_TRANSITION`, `REJECTION_REASON_REQUIRED`, `INVALID_CLIENT_ID`, `CLIENT_ID_TAKEN`, `INVALID_REDIRECT_URI`, `INVALID_SCOPE`, `APPLICATION_REQUIRED`, `RESERVED_APPLICATION` (un cliente no puede enlazarse a `cherrycore`).

## 7. Notas de seguridad

- `POST /api/company/invitations` está limitado por IP como `/api/auth` (`429` + `Retry-After`).
- `POST /api/account/delete` → `LAST_SUPER_ADMIN` si eres el último superadmin activo.
- `POST /api/auth/mfa/verify` y `/mfa/setup/confirm` → `401 INVALID_CREDENTIALS` si la cuenta se desactivó durante el login (se descarta el login a medias).
- Login social: `?error=SOCIAL_EMAIL_NOT_VERIFIED` también para cuentas de Google que no son Gmail ni Workspace.
- `GET /api/account/export`: `securityEvents[].ip` / `userAgent` son `null` cuando la acción la hizo otra persona.
- Los enlaces de email (`/password/reset`, `/email-change/confirm`) dan `INVALID_OR_EXPIRED_TOKEN` si la contraseña o el email cambiaron después de enviarlos.
- Subir logo (empresa y app) → `400 IMAGE_DIMENSIONS_TOO_LARGE` (más de 4096 px de ancho o alto) o `400 IMAGE_UNREADABLE`. La imagen se guarda sin metadatos (EXIF/GPS).
- Login: el paso del código caduca a los 10 min de poner la contraseña → `NO_PENDING_LOGIN` (volver a pedir la contraseña).
- `POST/PUT /api/admin/clients`: en producción solo URIs https (`INVALID_REDIRECT_URI` para http://localhost o con usuario:contraseña).
