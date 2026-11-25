# Guía de Configuración — Recargas PY

## 1. Permisos requeridos en Android
Al iniciar la aplicación por primera vez, concede los siguientes permisos:
- **Teléfono (`CALL_PHONE` y `READ_PHONE_STATE`)**: necesarios para detectar las SIMs de **Tigo** y **Claro PY** en dispositivos Dual-SIM y lanzar los códigos USSD directamente en la SIM correspondiente.
- **Notificaciones (`POST_NOTIFICATIONS`)**: opcional en Android 13+ para avisos.
- **Contactos (`READ_CONTACTS`)**: opcional; se solicita únicamente si tocas el ícono de agenda al ingresar un número destinatario.

## 2. Asistente USSD (Servicio de Accesibilidad)
La automatización interactiva de menús USSD funciona mediante `Asistente USSD Recargas PY` (`UssdAccessibilityService`):
1. Ingresa a **Ajustes → Automatización y seguridad**.
2. Toca **Activar** junto a *Asistente USSD (Accesibilidad)*.
3. Habilita **Asistente USSD Recargas PY** en los ajustes de Accesibilidad de Android.
   - *Nota para Android 13/14/15 (Ajustes restringidos)*: si el interruptor aparece deshabilitado al instalar desde APK externa, abre *Información de la app → menú ⋮ (arriba a la derecha) → Permitir ajustes restringidos*, o bien habilítalo por ADB con `adb shell appops set py.com.recargas.ussd ACCESS_RESTRICTED_SETTINGS allow`.
4. Si prefieres no activar Accesibilidad, la aplicación funciona igual en **Modo Asistido Manual**, mostrando paso a paso lo que debes ingresar en el diálogo USSD.

## 3. Seguridad y PINs cifrados
- En **Ajustes → Automatización y seguridad**, pulsa **Desbloquear credenciales**.
- Si tu teléfono tiene huella, PIN o patrón de pantalla configurado, Android solicitará autenticación biométrica o credencial del dispositivo (`BiometricPrompt`) antes de ver o modificar los PIN.
- Cada operador (**Tigo** y **Claro**) tiene su **PIN independiente** y su interruptor **Auto-ingreso de PIN**.
- Los PIN se almacenan cifrados mediante **Android Keystore (`AES-256-GCM`)** en almacenamiento privado y nunca se escriben en texto plano en catálogos, historial ni registros.
- Si Android bloquea la inserción automática o el PIN es rechazado, el asistente se detiene y permite ingreso manual sin simular éxito.

## 4. Personalización y accesos rápidos
En **Ajustes → Apariencia y personalización** y **Valores predeterminados y respaldo** puedes configurar:
- Tema (**Sistema**, **Claro**, **Oscuro**) y paleta de color principal.
- Tamaño de botones (**Normal**, **Grande**, **Extra**).
- Orden de operadores (**Tigo → Claro** o **Claro → Tigo**).
- Orden y ocultamiento de categorías no utilizadas.
- Operador e importe predeterminados.
- **Restaurar configuración original**: revierte todas las preferencias a sus valores de fábrica.
