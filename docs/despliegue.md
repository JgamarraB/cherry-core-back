# Despliegue en producción (Ubuntu 24.04)

Todo corre con Docker Compose en una sola VM. Ficheros en [`deploy/`](../deploy):

| Fichero | Qué es |
|---|---|
| `docker-compose.prod.yml` | Postgres 17, RustFS, backend, frontal (nginx) y Caddy |
| `Caddyfile` | HTTPS automático (Let's Encrypt) y reparto por dominio |
| `.env.prod.example` | Todas las variables, comentadas. Se copia a `.env` |
| `scripts/backup-postgres.sh` · `restore-postgres.sh` | Copias de la base de datos |

```
Internet ─443─▶ Caddy ─┬─ auth.tudominio.com  ─▶ frontal (nginx) ─┬─ SPA
                       │                                          └─ /api /oauth2 /login/oauth2 /.well-known /connect /userinfo ─▶ backend ─▶ Postgres, RustFS
                       └─ media.tudominio.com ─▶ RustFS (solo GET de companies/*/public/*: logos)
```

Solo se publican los puertos 80 y 443. Postgres, RustFS y el backend no son accesibles desde fuera.

## 1. Preparar la VM

```bash
# Docker oficial (incluye el plugin compose)
sudo apt-get update && sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update && sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER   # volver a entrar en la sesión

# Cortafuegos: solo SSH y web
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp && sudo ufw enable
```

> Docker publica puertos saltándose `ufw`; por eso el compose solo publica los de Caddy.

Mínimo recomendado: 2 vCPU, 4 GB de RAM (la compilación del backend la usa), 40 GB de disco.

## 2. DNS

Dos registros `A` (y `AAAA` si hay IPv6) hacia la IP pública de la VM:

- `auth.tudominio.com` → login, API y frontal (`APP_DOMAIN`)
- `media.tudominio.com` → logos públicos (`MEDIA_DOMAIN`)

Deben resolver **antes** del primer arranque: Caddy pide los certificados al arrancar.

## 3. Código, claves y `.env`

```bash
sudo mkdir -p /opt/cherrycore && sudo chown $USER /opt/cherrycore && cd /opt/cherrycore
git clone <repo>/cherry-core-back.git
git clone <repo>/cherry-core-front.git          # al lado: ../../cherry-core-front desde deploy/
cd cherry-core-back/deploy

cp .env.prod.example .env && chmod 600 .env
nano .env   # rellenar TODO (dominios, contraseñas, Brevo, SUPER_ADMIN_EMAIL...)

# Clave de firma de los tokens (una vez; guárdala también fuera de la VM)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt-private.pem
openssl rsa -in secrets/jwt-private.pem -pubout -out secrets/jwt-public.pem
sudo chown 10001:10001 secrets/*.pem && sudo chmod 400 secrets/*.pem   # 10001 = usuario del contenedor
```

Para generar secretos: `openssl rand -base64 32`. **`CHERRYCORE_ENCRYPTION_KEY` y la clave JWT no se pueden perder**: guárdalas en el gestor de contraseñas del equipo.

Brevo: verificar el dominio del remitente (SPF y DKIM) y crear una *SMTP key* (Brevo → SMTP & API).

Login social (opcional): crear `config/application-prod.yaml` con el bloque de `docs/arranque.md` y las variables `GOOGLE_*` / `APPLE_*` del `.env`. La clave de Apple va en `secrets/apple-key.p8` (mismos permisos que las `.pem`).

## 4. Primer arranque

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps        # todo "healthy" en 1-2 minutos
docker compose -f docker-compose.prod.yml logs -f backend caddy
```

Comprobar:

```bash
curl -s https://auth.tudominio.com/.well-known/openid-configuration | head -c 200   # "issuer":"https://auth.tudominio.com"
```

## 5. Primer superadmin

Al arrancar con `SUPER_ADMIN_EMAIL` y sin ningún superadmin, el backend crea la cuenta de backoffice y le envía un email para **elegir contraseña** (log: `First super admin created`).

1. Abrir el enlace del email y poner la contraseña.
2. Entrar en `https://auth.tudominio.com/login` como **Empresa** (backoffice): pide configurar el MFA con una app autenticadora. Guardar los códigos de recuperación.
3. Vaciar `SUPER_ADMIN_EMAIL` en `.env` (ya no hace nada, pero así queda claro) y `docker compose -f docker-compose.prod.yml up -d`.

> `scripts/dev/activar-superadmin.sh` es **solo para local** (lee el email de Mailpit).

Si el email no llega: revisar `logs backend` (errores SMTP) y el panel de Brevo. Se puede pedir otro enlace desde "¿Olvidaste tu contraseña?" en el login de empresa.

## 6. Dar de alta Lirgar (cliente OAuth)

Desde el panel de superadmin (`/admin`), siguiendo [integracion-apps.md](integracion-apps.md):

1. **Aplicaciones** → crear `lirgar`.
2. **Clientes OAuth** → crear `lirgar-web` (`CUSTOMER`, confidencial, redirect `https://lirgar.tudominio.com/login/oauth2/code/cherrycore`) y `lirgar-backoffice` (`STAFF`, app `lirgar`, acceso de máquina).
3. Copiar cada **secreto** (solo se muestra una vez) a los secretos del backend de Lirgar.
4. Lirgar apunta a `issuer-uri: https://auth.tudominio.com`.
5. **Empresas** → activar la app para cada empresa aprobada.

## 7. Actualizar

```bash
cd /opt/cherrycore
git -C cherry-core-back pull && git -C cherry-core-front pull
cd cherry-core-back/deploy
scripts/backup-postgres.sh                                   # copia antes de migrar
docker compose -f docker-compose.prod.yml up -d --build      # Flyway migra al arrancar
docker image prune -f
```

El backend para de forma ordenada (termina las peticiones en curso). Hay unos segundos de corte mientras arranca el nuevo; las pestañas abiertas se recargan solas si les falta código de la versión anterior.

Volver atrás: `git checkout <versión anterior>` en los dos repos y repetir. Si la versión nueva tenía migraciones, restaurar la copia (ver abajo).

## 8. Copias de seguridad

**Postgres, cada noche** (formato `pg_dump -Fc`, retención `BACKUP_RETENTION_DAYS`, por defecto 14 días):

```bash
sudo mkdir -p /var/backups/cherrycore && sudo chown $USER /var/backups/cherrycore
crontab -e
# 30 2 * * * /opt/cherrycore/cherry-core-back/deploy/scripts/backup-postgres.sh >> /var/backups/cherrycore/backup.log 2>&1
```

**Restaurar** (para el backend, sustituye los datos y lo arranca):

```bash
scripts/restore-postgres.sh /var/backups/cherrycore/cherrycore-20261106T023000Z.dump
```

**RustFS (logos)**: copiar el volumen de vez en cuando (son pocos datos):

```bash
docker run --rm -v cherrycore_storage_data:/data:ro -v /var/backups/cherrycore:/backup alpine:3.22 \
  tar czf /backup/storage-$(date -u +%Y%m%d).tar.gz -C /data .
```

**Fuera de la VM**: las copias en el mismo disco no sirven si se pierde la VM. Sincronizar `/var/backups/cherrycore` a otro sitio (otra VM, almacenamiento S3 externo con `rclone`…), junto con `.env` y `secrets/` cifrados. Probar una restauración al menos una vez antes del lanzamiento.

## 9. Logs

```bash
docker compose -f docker-compose.prod.yml logs -f --tail=200 backend   # o frontend, caddy, postgres, storage
docker compose -f docker-compose.prod.yml ps                            # estado y healthchecks
```

Rotación: 5 ficheros de 10 MB por contenedor. La IP real del cliente llega al backend (`X-Forwarded-For` de Caddy → nginx → Tomcat) y queda en la auditoría (`/admin` → Auditoría).

## 10. Checklist antes del lanzamiento

- [ ] DNS de `APP_DOMAIN` y `MEDIA_DOMAIN` apuntando a la VM; certificados emitidos (`logs caddy`).
- [ ] `.env` completo, `chmod 600`; secretos guardados también fuera de la VM.
- [ ] Clave JWT generada y con permisos `400` del usuario 10001.
- [ ] Brevo: dominio verificado (SPF/DKIM); llega el email de prueba (p. ej. "¿Olvidaste tu contraseña?").
- [ ] Superadmin activo con MFA; `SUPER_ADMIN_EMAIL` vaciado.
- [ ] `https://APP_DOMAIN/.well-known/openid-configuration` devuelve el `issuer` correcto (https, sin barra final).
- [ ] Los logos se ven en `https://MEDIA_DOMAIN/...` y `https://MEDIA_DOMAIN/` devuelve 404.
- [ ] Cookie de sesión con `Secure` (herramientas del navegador → Cookies).
- [ ] Cron de copias activo, copia externa configurada y **una restauración probada**.
- [ ] Textos legales (`/legal/terms`, `/legal/privacy`) completados y **revisados por un abogado**.
- [ ] `ufw` activo: solo 22, 80 y 443.
- [ ] Cliente OAuth de Lirgar creado y login de extremo a extremo probado.
