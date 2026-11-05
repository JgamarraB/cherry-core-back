# Arranque y configuración

## En local

1. Levantar Postgres, Mailpit y el almacenamiento S3 (RustFS):
   ```bash
   cd docker && docker compose up -d
   ```
2. Arrancar CherryCore con el **perfil `local`**:
   - Terminal: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
   - Eclipse: *Run Configurations → Spring Boot App → Profile: `local`* (o la variable `SPRING_PROFILES_ACTIVE=local`).
   - Para tener un superadmin: añadir la variable `SUPER_ADMIN_EMAIL=tu@email` y abrir el email en Mailpit.

| Qué | URL |
|---|---|
| Estado | http://localhost:8080/actuator/health |
| Emails enviados (Mailpit) | http://localhost:8025 |
| Ficheros (consola RustFS) | http://localhost:9001 |
| OpenID discovery | http://localhost:8080/.well-known/openid-configuration |

Sin perfil la app **no arranca a propósito**: faltan valores obligatorios y nunca se usan los de desarrollo por error.

## Tests

```bash
./mvnw test
```

Necesitan Docker: levantan su propio Postgres y RustFS (Testcontainers). No tocan tus datos locales.

## Imagen Docker

```bash
docker build -t cherrycore:latest .
```

JRE 21, usuario sin privilegios, parada ordenada. Sondas: `/actuator/health/liveness` y `/actuator/health/readiness`.

## Producción: variables de entorno

| Variable | Ejemplo / nota |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Postgres |
| `CHERRYCORE_ISSUER` | `https://auth.tudominio.com` (va en cada token) |
| `CHERRYCORE_FRONTEND_URL` | `https://auth.tudominio.com` |
| `CORS_ALLOWED_ORIGINS` | `https://auth.tudominio.com` |
| `CHERRYCORE_ENCRYPTION_KEY` | `openssl rand -base64 32` — **no cambiarla** (cifra los secretos MFA) |
| `JWT_KEY_ID` | `cherrycore-2026-11` |
| `JWT_PRIVATE_KEY_LOCATION`, `JWT_PUBLIC_KEY_LOCATION` | `file:/run/secrets/jwt-private.pem` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Brevo: `smtp-relay.brevo.com`, `587` |
| `STORAGE_ENDPOINT`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_BUCKET` | servidor S3 (RustFS/MinIO) |
| `STORAGE_PUBLIC_URL` | URL pública desde la que se sirven los logos |
| `SUPER_ADMIN_EMAIL` | primer superadmin (solo si aún no hay ninguno) |

**Clave de firma JWT** (una vez; guardarla como secreto, nunca en git):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

**Login social** (opcional; sin estas variables no aparece):

```yaml
spring.security.oauth2.client:
  registration:
    google:
      client-id: ${GOOGLE_CLIENT_ID}
      client-secret: ${GOOGLE_CLIENT_SECRET}
      scope: openid,email,profile
    apple:
      client-id: ${APPLE_SERVICE_ID}
      client-authentication-method: client_secret_post
      authorization-grant-type: authorization_code
      redirect-uri: "{baseUrl}/login/oauth2/code/apple"
      scope: openid,email,name
  provider:
    apple:
      authorization-uri: https://appleid.apple.com/auth/authorize
      token-uri: https://appleid.apple.com/auth/token
      jwk-set-uri: https://appleid.apple.com/auth/keys
      user-name-attribute: sub
cherrycore.social.apple:
  team-id: ${APPLE_TEAM_ID}
  key-id: ${APPLE_KEY_ID}
  private-key-location: file:/run/secrets/apple-key.p8
```

URLs de vuelta que hay que registrar en Google/Apple: `https://auth.tudominio.com/login/oauth2/code/google` y `.../apple`.

## Configuración de CherryCore

Todo lo ajustable está en `application.yaml` bajo `cherrycore:` (duración de tokens, intentos de login, política de contraseña, rate limit, sesión, CORS, idiomas, limpieza de datos…). La lee una única clase, `CherryCoreProperties`. Si un valor es inválido, la app no arranca y dice cuál.
