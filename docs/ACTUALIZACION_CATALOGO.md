# Sistema Inteligente de Actualización del Catálogo

La aplicación **Recargas PY** separa completamente el catálogo de operadores del código APK, permitiendo actualizar precios, paquetes, vigencias, montos y opciones USSD sin reinstalar la aplicación.

## 1. Formato del catálogo JSON
El catálogo sigue el esquema definido en [catalog_v1.json](../catalog/catalog_v1.json):
- `schemaVersion`: entero (`1`).
- `catalogVersion`: versión semántica (ej. `"1.0.0"`, `"1.1.0"`).
- `updatedAt`: fecha/hora ISO-8601.
- `verificationLevel`:
  - `VERIFIED_USSD`: relevado en vivo sobre el menú USSD real.
  - `PUBLISHED_UNVERIFIED`: publicado en catálogo externo pero pendiente de verificación en USSD.
  - `PENDING`: flujo conocido pero sin lista confirmada.
  - `BROKEN`: código o paquete marcado como no operativo.
- `integritySha256` (opcional): hash SHA-256 en hexadecimal calculado sobre el JSON con `"integritySha256": null`.
- `operators`: lista extensible de operadores (`id`, `name`, `shortName`, `brandColorHex`, `ussdCode`, `simMatch`, `authMode`, `phoneFormat`, `directCodes`, `ussdRules`, `categories`).

> **Regla de seguridad**: el validador rechaza automáticamente cualquier JSON que contenga campos de PIN en texto plano, códigos USSD mal formados, precios ≤ 0 o estructura incompleta.

## 2. Cómo publicar y actualizar sin reinstalar la APK
### Opción A: URL HTTP/HTTPS configurable
1. Aloja el archivo JSON (por ejemplo, `catalog_v1.json`) en un servidor web, GitHub Raw o red local. De fábrica, la APK viene preconfigurada con:
   `https://raw.githubusercontent.com/Memorysinc/recargas-py-ussd/main/catalog/catalog_v1.json`
2. En la app, ve a **Ajustes → Catálogo y actualizaciones**.
3. Ingresa la **URL del catálogo JSON externo** y toca **Guardar URL**.
4. Configura el intervalo automático (`1`, `3`, `7`, `15` o `30` días) o pulsa **Actualizar ahora**.
   - La app también comprueba actualizaciones automáticamente al iniciar (sin bloquear si no hay internet).

### Opción B: Importación local de archivo JSON
1. Copia el archivo `.json` al teléfono.
2. En **Ajustes → Catálogo y actualizaciones**, toca **Importar JSON** y selecciona el archivo.

## 3. Comparación inteligente (Diff) y respaldo
Cada vez que entra un catálogo válido nuevo:
1. Se respalda automáticamente el catálogo anterior en almacenamiento interno (`backup_catalog.json`).
2. `CatalogDiffEngine` compara ambos catálogos y detecta:
   - Precios modificados.
   - Paquetes nuevos y paquetes eliminados.
   - Cambios de vigencia.
   - Opciones o montos agregados/eliminados.
   - Códigos que dejaron de funcionar (`BROKEN`).
3. Si los avisos están activos, aparece un banner en la pantalla principal con el resumen legible.
4. Si la actualización falla, no hay internet o el archivo es inválido, la app conserva intacto el último catálogo válido y permite **Restaurar anterior** en un toque.
