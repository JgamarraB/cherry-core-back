# API de login (para el frontend)

Lo que necesita el frontend de login de CherryCore (registro, login, verificación de email y recuperación de contraseña).

## Cómo encaja todo

```
Web de Lirgar ──► Backend de Lirgar (BFF) ──redirige──► CherryCore /oauth2/authorize
                                                              │ sin sesión
                                                              ▼
                                         Frontend de login  /login  ── POST /api/auth/customers/login
                                                              │ respuesta: redirectUrl
                                                              ▼
                                         navegador → redirectUrl → CherryCore devuelve "code" al BFF
                                         BFF canjea code → tokens (nunca llegan al navegador)
```

1. La app manda al usuario a `/oauth2/authorize`. Si no hay sesión, CherryCore lo redirige a `{frontend}/login`.
2. El frontend hace login con `POST /api/auth/customers/login`.
3. Si la respuesta trae `redirectUrl`, el frontend hace `window.location.href = redirectUrl` y el flujo OAuth continúa solo.
4. Si `redirectUrl` es `null`, el usuario entró directamente al login: muestra "sesión iniciada" o la página que toque.

## Reglas comunes

- **Mismo dominio:** el frontend de login y CherryCore deben servirse bajo el mismo dominio (p. ej. `auth.tudominio.com`, con `/api` y `/oauth2` pasados a CherryCore). En local, `localhost:4200` → `localhost:8080` funciona.
- **Cookies:** todas las llamadas con `credentials: 'include'` (en Angular, `withCredentials: true`).
- **CSRF:** al arrancar, `GET /api/auth/csrf`. Después, en cada POST, enviar la cookie `XSRF-TOKEN` en la cabecera `X-XSRF-TOKEN` (Angular lo hace solo con `withXsrfConfiguration`).
- **Idioma:** enviar `Accept-Language` (`es` o `en`). Decide el idioma de los emails de la cuenta.
- **Respuestas:** `{ "ok": true, "data": ... }` o `{ "ok": false, "error": { "code", "description", "fields"? } }`. Se traduce por `code`.

## Endpoints

Base: `/api/auth`

| Método y ruta | Body | OK | Errores posibles |
|---|---|---|---|
| `GET /csrf` | — | 200 `{cookieName, headerName}` | — |
| `GET /session` | — | 200 `{authenticated, accountType}` | — |
| `POST /customers/register` | `{email, password, firstName, lastName, phone?, acceptTerms}` | **202** siempre (también si el email ya existe) | `VALIDATION_ERROR`, `INVALID_EMAIL`, `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG`, `PASSWORD_CONTAINS_EMAIL`, `TERMS_NOT_ACCEPTED`, `INVALID_NAME`, `INVALID_PHONE` |
| `POST /email-verification/confirm` | `{token}` | 200 | `INVALID_OR_EXPIRED_TOKEN` |
| `POST /customers/email-verification/resend` | `{email}` | **202** siempre | `VALIDATION_ERROR` |
| `POST /customers/login` | `{email, password}` | 200 `{redirectUrl}` | `INVALID_CREDENTIALS` (401), `EMAIL_NOT_VERIFIED` (403) |
| `POST /logout` | — | 200 | — |
| `POST /customers/password/forgot` | `{email}` | **202** siempre | `VALIDATION_ERROR` |
| `POST /password/reset` | `{token, newPassword}` | 200 | `INVALID_OR_EXPIRED_TOKEN`, `PASSWORD_*` |

Errores comunes a todos: `CSRF_TOKEN_INVALID` (403), `TOO_MANY_REQUESTS` (429, con cabecera `Retry-After`), `MALFORMED_REQUEST` (400), `INTERNAL_ERROR` (500).

**Páginas del frontend** a las que llevan los emails (`?token=...`):

- `/verify-email?token=…` → llamar a `/email-verification/confirm`.
- `/reset-password?token=…` → pedir la nueva contraseña y llamar a `/password/reset`.

## Comportamientos a tener en cuenta en la UI

- **El login no dice si el email existe:** un email inexistente, una contraseña incorrecta y una cuenta bloqueada devuelven todos `INVALID_CREDENTIALS`. Mensaje sugerido: "Email o contraseña incorrectos".
- **`EMAIL_NOT_VERIFIED`:** mostrar la opción "reenviar email de verificación".
- **Bloqueo:** tras 5 intentos fallidos la cuenta se bloquea 15 minutos y el usuario recibe un email. La UI no lo sabe (sigue viendo `INVALID_CREDENTIALS`).
- **Respuestas 202:** en registro, reenvío y "olvidé mi contraseña", mostrar siempre "Si el email es correcto, recibirás un mensaje".
- **Contraseña:** mínimo 10 caracteres, máximo 128 y sin contener el email. No se exigen mayúsculas ni símbolos.
- **Teléfono:** formato internacional (`+34 600 11 12 22`; los espacios se aceptan).
- **Restablecer la contraseña cierra todas las sesiones** del usuario.

## API del cliente autenticado (token Bearer)

Para las apps (desde su backend). Base: `/api/customers/me`

| Método y ruta | Body | OK |
|---|---|---|
| `GET /` | — | 200 perfil `{id, email, firstName, lastName, phone, locale}` |
| `PUT /` | `{firstName, lastName, phone?, locale?}` | 200 perfil |
| `GET /addresses` | — | 200 lista |
| `POST /addresses` | `{line1, line2?, city, postalCode, region?, countryCode, isDefault}` | 201 |
| `PUT /addresses/{id}` | igual | 200 |
| `DELETE /addresses/{id}` | — | 200 |

## Para las apps (BFF)

- **Discovery:** `GET /.well-known/openid-configuration`.
- **Flujo:** Authorization Code + **PKCE obligatorio**. Scopes: `openid profile email`.
- **Access token:** 15 min. Claims: `sub` (UUID de la cuenta) y `account_type` (`CUSTOMER`/`STAFF`).
- **Refresh token:** 30 días (clientes) / 12 h (backoffice). Cambia en cada uso: si se reutiliza uno antiguo, se rechaza.
- **Validación en cada app:** firma con `/oauth2/jwks`, más `iss` y caducidad. Con Spring basta `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
