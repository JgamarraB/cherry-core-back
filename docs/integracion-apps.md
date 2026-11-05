# Cómo se conecta una app a CherryCore

Ejemplo: Lirgar Mobility (backend Spring + web Astro + backoffice Angular).

## 1. Dar de alta la app (panel de superadmin)

1. **App del catálogo**: `POST /api/admin/applications` `{code: "lirgar", name: "Lirgar Mobility"}`.
2. **Clientes OAuth** (uno por cada "puerta"):

| Cliente | `audience` | `applicationCode` | `machineAccess` | Para |
|---|---|---|---|---|
| `lirgar-web` | `CUSTOMER` | — | no | la web de clientes |
| `lirgar-backoffice` | `STAFF` | `lirgar` | **sí** | el backoffice + sincronizar permisos |

Tipo `CONFIDENTIAL` (patrón BFF). El secreto se muestra una sola vez: guardarlo como secreto del backend de Lirgar.

3. **Activar la app** para cada empresa: `PUT /api/admin/companies/{id}/applications/{appId}` `{enabled: true}`.

## 2. Login (patrón BFF)

El navegador nunca ve los tokens: los recibe y guarda el **backend** de Lirgar, que da a su web una cookie de sesión propia.

```yaml
# application.yaml del backend de Lirgar (Spring Security OAuth2 Client)
spring.security.oauth2.client:
  registration:
    cherrycore:
      provider: cherrycore
      client-id: lirgar-web
      client-secret: ${LIRGAR_WEB_CLIENT_SECRET}
      authorization-grant-type: authorization_code
      scope: openid,profile,email
  provider:
    cherrycore:
      issuer-uri: https://auth.tudominio.com
```

- PKCE es obligatorio (Spring lo envía solo).
- Redirect URI registrada: `https://lirgar.tudominio.com/login/oauth2/code/cherrycore` (exacta, https).
- Logout: revocar el refresh token (`POST /oauth2/revoke`) y redirigir a `/connect/logout`.

## 3. Validar tokens en la API de la app

```yaml
spring.security.oauth2.resourceserver.jwt.issuer-uri: https://auth.tudominio.com
```

Claims:

| Claim | Clientes | Backoffice |
|---|---|---|
| `sub` | id de la cuenta (UUID) | id de la cuenta (UUID) |
| `account_type` | `CUSTOMER` | `STAFF` |
| `tenant_id`, `tenant_name` | — | empresa con la que entró |
| `tenant_role` | — | `OWNER`, `ADMIN`, `STAFF` o uno propio |
| `permissions` | — | permisos **de esta app** en esa empresa |
| `platform_role` | — | `SUPER_ADMIN` si lo es |

- Access token: 15 min. Refresh: 30 días (clientes) / 12 h (backoffice), **cambia en cada uso**.
- En cada refresh CherryCore vuelve a comprobar cuenta, pertenencia, empresa y app: si algo se ha desactivado, no hay token nuevo.
- Toda consulta de backoffice debe filtrar por `tenant_id` del token (nunca de la URL).
- La app guarda sus propios datos del cliente en su tabla (p. ej. `customers` de Lirgar con el carnet de conducir), enlazada por `sub`.

## 4. Sincronizar los permisos al arrancar

El backend de la app pide un token de máquina y envía su lista completa de permisos. Lo que ya no envíe se retira (y se quita de los roles).

```http
POST /oauth2/token
Authorization: Basic base64(lirgar-backoffice:SECRETO)
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=catalog.sync
```

```http
PUT /api/apps/permissions
Authorization: Bearer {access_token}
Content-Type: application/json

{"permissions": [
  {"code": "vehicles.manage", "description": "Gestionar vehículos"},
  {"code": "reservations.view", "description": "Ver reservas"}
]}
```

Formato de código: minúsculas, `recurso.accion`. La app solo puede modificar su propio catálogo.
