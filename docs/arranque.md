# Arranque y configuración

## En local

1. Levantar Postgres y Mailpit:
   ```bash
   cd docker && docker compose up -d
   ```
2. Arrancar CherryCore con el **perfil `local`**:
   - Terminal: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
   - Eclipse: *Run Configurations → Spring Boot App → Profile: `local`* (o la variable de entorno `SPRING_PROFILES_ACTIVE=local`).
3. Comprobar:

| Qué | URL |
|---|---|
| Estado | http://localhost:8080/actuator/health |
| Emails enviados (Mailpit) | http://localhost:8025 |
| OpenID discovery | http://localhost:8080/.well-known/openid-configuration |

Sin perfil la app **no arranca a propósito**: faltan valores obligatorios y nunca se usan los de desarrollo por error.

## Tests

```bash
./mvnw test
```

Necesitan Docker (levantan su propio Postgres con Testcontainers). No tocan tu base de datos local.

## Perfiles

| Perfil | Para qué | Notas |
|---|---|---|
| (base) `application.yaml` | Común a todos | Sin valores inseguros |
| `local` | Tu máquina | Clave JWT efímera, cookie sin `Secure`, Mailpit, cliente `lirgar-web` de prueba |
| `test` | Tests automáticos | Lo activan los tests solos |
| `prod` | Producción | Todo por variables de entorno (abajo) |

## Producción: variables de entorno

| Variable | Ejemplo |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | |
| `CHERRYCORE_ISSUER` | `https://auth.tudominio.com` |
| `CHERRYCORE_FRONTEND_URL` | `https://auth.tudominio.com` |
| `CORS_ALLOWED_ORIGINS` | `https://auth.tudominio.com` |
| `JWT_KEY_ID` | `cherrycore-2026-11` |
| `JWT_PRIVATE_KEY_LOCATION`, `JWT_PUBLIC_KEY_LOCATION` | `file:/run/secrets/jwt-private.pem` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | datos SMTP del proveedor (p. ej. Brevo) |

Los clientes OAuth de producción (redirect URIs reales y secretos por variable de entorno) se definen en `application-prod.yaml` bajo `cherrycore.oauth.clients`.

**Generar la clave de firma JWT** (una vez; guardarla como secreto, nunca en git):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

## Configuración de CherryCore

Todo lo ajustable está en `application.yaml` bajo `cherrycore:` (duración de tokens, intentos de login, política de contraseña, rate limit, sesión, CORS, idiomas…). La lee una única clase, `CherryCoreProperties`. Si un valor es inválido, la app no arranca y dice cuál.
