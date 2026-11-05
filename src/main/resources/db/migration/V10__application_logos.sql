/*
 * Logo de cada app del catálogo (se muestra en el login cuando la app manda
 * al usuario a CherryCore). Ruta en el almacenamiento:
 * applications/{id}/public/branding/logo-{id}.{ext}, o NULL si no tiene.
 */
ALTER TABLE applications ADD COLUMN logo_key VARCHAR(300);
