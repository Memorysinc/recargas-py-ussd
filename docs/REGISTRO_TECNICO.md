# Registro Técnico y Evidencias — Recargas PY

## 1. Resumen por etapas

| Etapa | Estado | Entregables y decisiones clave |
|---|---|---|
| **Etapa 1** | Completada | Instalación de Eclipse Temurin JDK 17; creación de [catalog_v1.json](../catalog/catalog_v1.json) y [catalog_v2_sample.json](../catalog/catalog_v2_sample.json) sin PINs; proyecto Kotlin + Compose + Material 3 (`compileSdk 35`, `minSdk 26`, Gradle `8.11.1`, AGP `8.7.3`, Kotlin `2.0.21`). |
| **Etapa 2** | Completada | `PinVault` (Android Keystore `AES-256-GCM`), `DeviceAuthManager` (`BiometricPrompt`), `PhoneFormatter`, `SimSelector` (Dual-SIM `TelecomManager` + `SubscriptionManager`), `UssdPlanBuilder`, `UssdSessionCoordinator` (watchdog 25 s) y `UssdAccessibilityService` con parada obligatoria antes de confirmar. |
| **Etapa 3** | Completada | `CatalogValidator` (esquema, SHA-256 opcional, bloqueo de PINs en claro), `CatalogDiffEngine`, `CatalogRepository` (respaldo, rollback y modo offline), `CatalogSyncWorker` (`WorkManager` cada 1/3/7/15/30 días). |
| **Etapa 4** | Completada | Room (`AppDatabase`: historial, favoritos pre-poblados y números frecuentes), DataStore (`PreferencesRepository` con `resetToDefaults`) e interfaz de 4 secciones (**Operar**, **Favoritos**, **Historial**, **Ajustes**). |
| **Etapa 5** | Completada | 9/9 pruebas unitarias en verde, APK generada en [RecargasPY.apk](../releases/RecargasPY.apk) y batería E2E ejecutada vía ADB en dispositivo real Android 15 (Dual-SIM). |

## 2. Decisiones técnicas y limitaciones encontradas en Android 15

1. **Selección automática de SIM (Dual-SIM)**:
   - Se resolvió mediante `SubscriptionManager.activeSubscriptionInfoList` (`mcc=744/mnc=04` para `TIGO`; `mcc=744/mnc=02` para `Claro PY`) emparejado con `TelecomManager.placeCall(..., EXTRA_PHONE_ACCOUNT_HANDLE)`.
   - Verificado en vivo: lanzó `*888#` y `*321#` directamente en la SIM correcta sin abrir el selector manual del sistema; `UssdAccessibilityService` mantiene además soporte de respaldo por si aparece el selector de `com.android.dialer`.
2. **Accesibilidad y restricciones de Android 15 (`ACCESS_RESTRICTED_SETTINGS`)**:
   - En Android 13–15, las APKs instaladas fuera de Play Store requieren autorizar ajustes restringidos para habilitar `UssdAccessibilityService`.
   - Cuando Accesibilidad está desactivada, Android no permite leer ni escribir en el diálogo `com.android.phone`; en ese escenario la app activa el **Modo Asistido Manual** (guía paso a paso y registro honesto sin simular completitud).
3. **Bloqueo de pantalla y `BiometricPrompt`**:
   - Si el dispositivo no tiene huella, patrón o PIN de pantalla activo (`BIOMETRIC_ERROR_NONE_ENROLLED`), `DeviceAuthManager` muestra un aviso con acceso directo a Ajustes de seguridad del sistema y un desbloqueo explícito de respaldo.
4. **Seguridad de edición de PIN (`SettingsScreen`)**:
   - El campo inicia siempre vacío al editar un PIN para no exponer el valor previo ni concatenar dígitos accidentalmente.
5. **Códigos directos no verificados**:
   - Ningún código directo USSD (como `*888*<PIN>*1#`) fue verificado contra el operador por riesgo de ejecutar recargas reales. `UssdPlanBuilder` mantiene el modo directo deshabilitado mientras el catálogo no incluya un código verificado y explica la caída automática a modo asistido.
6. **Resultado honesto en cancelación de Claro (`*321#`)**:
   - Al cancelar la confirmación de Claro con `2`, el operador respondió `"Gracias por usar el servicio Claro."` (mensaje genérico). El coordinador registró `DESCONOCIDA` en lugar de asumir `COMPLETADA`.

## 3. Pruebas ejecutadas y resultados reales

### 3.1 Pruebas unitarias (`.\gradlew.bat testDebugUnitTest`)
- **9/9 pruebas pasaron** (`CatalogValidatorTest`, `CatalogDiffEngineTest`, `PhoneFormatterTest`, `UssdPlanBuilderTest`).

### 3.2 Pruebas reales en dispositivo Android 15 Dual-SIM vía ADB
> **Ninguna recarga ni compra real fue confirmada.**

| Prueba | Resultado observado | Evidencia pública |
|---|---|---|
| **Detección Dual-SIM y separación por operador** | `SIM TIGO (S2)` y `SIM Claro PY (S1)` detectadas; solo se muestran las operaciones del operador seleccionado. | [01_inicio_tigo.png](screenshots/01_inicio_tigo.png), [02_inicio_claro.png](screenshots/02_inicio_claro.png) |
| **Cifrado de PIN con Android Keystore** | `shared_prefs/recargas_pin_vault.xml` almacena únicamente `enc_pin_tigo` y `enc_pin_claro` cifrados en Base64; auditoría `run-as` confirmó ausencia de PINs en claro (`CLEAN_NO_PLAINTEXT_PIN`). | [03_ajustes_seguridad.png](screenshots/03_ajustes_seguridad.png) |
| **Tigo → Consultas → Saldo (solo lectura)** | Auto-SIM Tigo + auto-PIN + opción `6` automáticos; lectura del saldo devuelto por Tigo y registro `Completada`. | Verificado en vivo vía ADB |
| **Tigo → Recarga Gs. 5.000** | Resumen previo, avance automático hasta confirmación `1.SI, 2.NO`, parada segura, cancelación con `2` (`Transaccion cancelada`) y registro `Cancelada`. | Verificado en vivo vía ADB |
| **Claro → Consultas → Saldo disponible** | Auto-SIM Claro PY + `6 -> 3` + auto-PIN; lectura del saldo disponible y registro `Completada`. | Verificado en vivo vía ADB |
| **Claro → Recarga Gs. 1.000** | Avance automático (`1 -> destino -> monto -> PIN`), parada segura en `Confirma Venta de Recarga`, cancelación con `2`. | Verificado en vivo vía ADB |
| **Actualización inteligente (HTTP), rechazo corrupto, rollback y offline** | Actualización `1.0.0 → 1.1.0` detectó los 5 cambios; JSON con PIN en claro fue rechazado; `Restaurar anterior` volvió a `v1.0.0`; sin servidor mantuvo el último catálogo válido. | [10_catalogo_diff.png](screenshots/10_catalogo_diff.png) |
| **Personalización (Tema oscuro)** | Cambio inmediato a tema oscuro y retorno limpio a tema del sistema. | [11_tema_oscuro.png](screenshots/11_tema_oscuro.png) |
