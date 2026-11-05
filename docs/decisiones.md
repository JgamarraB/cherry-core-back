# Decisiones de CherryCore

Solo lo imprescindible: qué decidimos y por qué. Si una decisión cambia, se edita aquí y se anota la fecha.
El modelo de datos está en [esquema-bd.md](esquema-bd.md).

## Qué es CherryCore

El servicio central de la plataforma. Hace tres cosas:

1. **Autenticación**: login de todos los usuarios y emisión de tokens JWT.
2. **Empresas**: alta de empresas (tenants) y de su personal.
3. **Suscripciones** (más adelante): cobro a las empresas por usar las apps.

Las demás apps (Lirgar Mobility, etc.) **no gestionan logins**: solo validan el token de CherryCore.

## D1 · Base de datos: PostgreSQL 17 — 2026-11-05

- Cada proyecto tiene su propia base de datos; no es obligatorio que todos usen el mismo motor.
- Proyectos nuevos en Postgres por defecto. Los existentes en MySQL se quedan como están.
- En local CherryCore usa el puerto **5433** (el 5432 lo usa lirgar-mobility).

## D2 · Dos tipos de usuario, separados — 2026-11-05

| Tipo | Quién es | Pertenece a |
|---|---|---|
| **Cliente final** | Quien alquila coches u otros servicios | Nadie: cuenta **global** de la plataforma |
| **Usuario de backoffice** | Personal de una empresa | Una o varias empresas, con un rol en cada una |

- Son cuentas **independientes**: el mismo email puede existir como cliente y como backoffice, pero son dos cuentas distintas.
- CherryCore guarda solo los **datos comunes** del cliente. Cada app crea su tabla `customers` con lo que necesite (p. ej. carnet de conducir), enlazada por el id del cliente.

## D3 · Seguridad del login — 2026-11-05

- **Login social** (Google y Apple). Se vincula a una cuenta existente solo si el proveedor garantiza el email. Los clientes nuevos aceptan los términos antes de crear la cuenta. En backoffice nunca crea cuentas.
- **MFA** con app autenticadora (TOTP) + 10 códigos de recuperación: obligatorio en backoffice (se configura en el primer login), opcional para clientes. Un código no se puede reutilizar; 5 fallos anulan el login a medias. El secreto se guarda cifrado (AES-256-GCM).
- **Email verificado** obligatorio para poder entrar.
- Contraseñas con **Argon2id**. Mínimo 10 caracteres, sin reglas de mayúsculas/símbolos (NIST).
- **No se revela si un email existe**: mismo error y mismo tiempo de respuesta en login; 202 siempre en registro, reenvío y "olvidé mi contraseña".
- **Bloqueo** de 15 min tras 5 fallos (con aviso por email) + **límite de peticiones por IP**.
- Cambiar la contraseña **cierra todas las sesiones** y anula los refresh tokens.
- El contador de fallos es **de la cuenta**: contraseña, códigos MFA y reautenticaciones de "mi cuenta" suman en el mismo contador (repetir el login no da más intentos).
- Cambios sensibles (contraseña, email, borrado) piden la contraseña actual; las cuentas sin contraseña (solo login social) necesitan haber entrado en los últimos 10 minutos.
- Límite de peticiones por IP (las IPv6 se agrupan por bloque /64) en `/api/auth` y `/api/account`.
- Una cuenta **sin verificar** pudo crearla cualquiera con un email ajeno: si después se vincula por login social, su contraseña se elimina.

## D4 · CherryCore es nuestro Identity Provider — 2026-11-05

- Se construye con **Spring Authorization Server** (OAuth2 / OpenID Connect).
- Los JWT se firman con clave privada (RS256). La clave pública se publica en `/oauth2/jwks`; las apps validan con esa URL. **No se comparten secretos.**
- **El login se hace en CherryCore**, no en cada app: la app redirige, el usuario entra y vuelve con el token. Las apps nunca ven la contraseña.
- **Sin JSP/Thymeleaf.** Las pantallas de login, registro y MFA son un **frontend propio** (SPA). El backend solo expone JSON.
- La sesión de login de CherryCore es una cookie `HttpOnly` guardada en Postgres (Spring Session): permite varias instancias.
- Las apps usan el patrón **BFF**: su backend recibe y guarda los tokens; el navegador solo tiene una cookie. PKCE obligatorio y refresh tokens que cambian en cada uso.
- Contrato para el frontend: [api.md](api.md). Cómo se conecta una app: [integracion-apps.md](integracion-apps.md).

## D5 · Formato de respuestas y errores — 2026-11-05

```json
{ "ok": true,  "data": { } }
{ "ok": false, "error": { "code": "CUSTOMER_EMAIL_ALREADY_EXISTS", "description": "Email already registered", "fields": [] } }
```

- Siempre con su **estado HTTP** correcto.
- `code` es estable; el **front traduce** por `code`. `description` va en inglés.
- Cada módulo tiene su **enum de errores** (`CustomerErrorCode`…) que implementa `ErrorCode`: código + descripción + **categoría** (`NOT_FOUND`, `CONFLICT`…).
- La categoría se traduce a estado HTTP en **un único sitio** (`HttpStatusResolver`). El dominio no sabe nada de HTTP.
- Todo se gestiona en un único `GlobalExceptionHandler`. Nunca se devuelven mensajes internos, trazas ni el valor enviado en un campo inválido.
- Excepción: los endpoints del protocolo OAuth2 (`/oauth2/**`) usan el formato estándar OAuth2, porque las librerías cliente lo esperan.

## D6 · Identificadores UUID — 2026-11-05

- Todos los ids son **UUID v7** (ordenables por fecha). Evitan que alguien adivine registros probando `/1`, `/2`, `/3`…
- ⚠️ Lirgar usa `BIGINT` para `tenant_id` y `customer_id`: hay que pasarlos a `UUID`.

## D7 · Empresas y roles — 2026-11-05

- Las empresas se registran solas pero quedan **pendientes de aprobación** por un **superadmin** de la plataforma (nosotros).
- El superadmin puede ver y gestionar todas las apps.
- Cada empresa crea **sus propios roles**. Siempre tiene `OWNER`, `ADMIN` y `STAFF`, que **no se pueden borrar** ni renombrar. `OWNER` tiene siempre todos los permisos; los de `ADMIN` y `STAFF` los ajusta la empresa.
- Solo un `OWNER` da o quita el rol `OWNER`, y siempre queda al menos uno.
- **Permisos** (como Shopify): cada app **sincroniza** su catálogo al arrancar con un token de máquina; cada empresa los combina en sus roles. CherryCore se registra a sí misma con los permisos de gestión de empresa.
- Las apps se activan por empresa a mano desde el panel; más adelante lo hará la **suscripción de Stripe**.
- Backoffice en varias empresas: elige con cuál entrar. El token de la app lleva `tenant_id`, `tenant_role` y `permissions` (solo los de esa app), y se revalida en cada refresh.

## D8 · Datos y auditoría — 2026-11-05

- **Borrado lógico** en todas las tablas (`deleted_at`, `deleted_by`).
- **Auditoría** en todas las tablas: `created_at`, `created_by`, `updated_at`, `updated_by`.
- Fechas siempre en **UTC** (`TIMESTAMPTZ`).

## D9 · Configuración en un solo sitio — 2026-11-05

- Todo lo ajustable (duración de tokens, intentos de login, CORS, emails…) está en `application.yaml` bajo `cherrycore:`, leído por una sola clase `CherryCoreProperties`.
- Tokens: access **15 min**; refresh **30 días** clientes / **12 h** backoffice.

## D10 · Envío de emails — 2026-11-05

- El código usa una interfaz `EmailSender`; cambiar de proveedor es cambiar la configuración SMTP o un adaptador.
- Se envían **en segundo plano** y solo si la operación se ha guardado.
- **Idiomas**: español (por defecto) e inglés. Cada cuenta guarda su idioma. Añadir uno = añadir `i18n/emails_xx.properties`.
- **Desarrollo**: Mailpit (servidor de correo local en Docker, no envía nada real).
- **Producción**: **Brevo** por SMTP (empresa de la UE, 300 emails/día gratis). Solo cambian las variables `MAIL_*`.

## D11 · Frontend propio de CherryCore — 2026-11-05

- Un único frontend con: login/registro/recuperación, "mi cuenta", gestión de la empresa y **panel de superadmin**.
- Se sirve en el **mismo dominio** que la API y usa la **sesión con cookie + CSRF** (no tokens). Los tokens son solo para las apps externas.

## D12 · Ficheros (logos y multimedia) — 2026-11-05

- Almacenamiento **compatible con S3**: el código no depende del proveedor.
- MinIO dejó de publicar imágenes Docker gratuitas: usamos **RustFS** (gratuito, Apache 2.0, compatible con MinIO) en local, tests y **también en nuestros servidores** (máquinas virtuales Ubuntu).
- Una carpeta por empresa: `companies/{id}/public/...` (público, p. ej. logo). El resto del bucket es privado.
- El tipo de imagen se comprueba por el contenido (PNG, JPEG, WEBP). **Nunca SVG** (puede llevar JavaScript).
- Los emails de una empresa (invitaciones, aprobación) llevan **su logo o, si no tiene, su nombre comercial**. Los de cuenta llevan la marca de la plataforma.

## D13 · Borrado de cuenta (RGPD) — 2026-11-05

- Borrado lógico + **anonimización** (nombre, email, teléfono). El email queda libre para registrarse otra vez.
- Cada módulo anonimiza lo suyo al recibir el evento de borrado. No se permite si es el único `OWNER` de una empresa.
- Avisar a las apps (webhooks) queda para más adelante.

## D14 · Mantenimiento y despliegue — 2026-11-05

- Limpieza diaria: autorizaciones OAuth y tokens caducados (7 días) y auditoría (365 días). Configurable.
- Imagen Docker propia (JRE 21, usuario sin privilegios, parada ordenada) y sondas `/actuator/health/liveness` y `/readiness`.
- El primer superadmin se crea al arrancar con `SUPER_ADMIN_EMAIL`.

## Pendiente

- Tecnología del frontend de CherryCore.
- Suscripciones con Stripe (fase 2 del producto).
- Webhooks a las apps (cuenta borrada, empresa suspendida...).
- Passkeys (WebAuthn) como alternativa al TOTP.
