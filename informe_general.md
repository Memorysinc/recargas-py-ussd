# Relevamiento USSD — Tigo y Claro Paraguay

## Entorno y seguridad
- ADB conectado a Redmi Note 9S (Android 15).
- Todos los PINs, identificadores de transacción y números telefónicos personales fueron removidos o anonimizados para publicación en GitHub. Dentro de la aplicación Android, los PINs se almacenan cifrados mediante Android Keystore (`AES-256-GCM`).
- No se confirmó recarga ni se activó pack. El código directo con PIN y opción de Recarga no se probó.

## Tigo (`*888#`)
Código inicial `*888#` verificado. Tras elegir SIM TIGO y autenticar con PIN, apareció: `1 Recarga; 2 Llamada+SMS; 3 Combos; 4 ILIMITADOS; 5 Roaming PRE; 6 Saldo; 7 PIN; 8 Historial`.

- **Opción 1 (`Recarga`)**: Pide `Ingrese el numero del destinatario` (`0981000000`), luego `Digite monto:` con 11 opciones (`1. Gs 5000`, `2. Gs 10000`, `3. Gs 15000`, `4. Gs 30000`, `5. Gs 35000`, `6. Gs 40000`, `7. Gs 50000`, `8. Gs 100000`, `9. Gs 8000`, `10. Gs 20000`, `11. Gs 7000`). Al elegir `1. Gs 5000`, mostró `Transfiere Gs 5000 a 0981000000? 1.SI, 2.NO` y se canceló con `2` (`Transaccion cancelada`).
- **Opción 2 (`Llamada+SMS`)**: Para `0981000000` aparecieron: `25 min + 150 SMS` por Gs. 35.000 durante 24 h; `ILIM a Tigo + 150 min TD` por Gs. 8.000 durante 3 días. Se abrió el resumen de la segunda (`1 Sí`, `2 No`) sin confirmar.
- **Opción 3 (`Combos`)**: Para `0981000000`: 1) `1G+Mi`, Gs. 8.000, 1 día; 2) `2GB+Min`, Gs. 12.000, 2 días; 3) `2,5GB+Min`, Gs. 16.000, 3 días; 4) `3GB+Min`, Gs. 20.000, 4 días; 5) `4GB+Min`, Gs. 27.000, 6 días; 6) `6GB+Min`, Gs. 33.000, 8 días; 7) `10GB+Min`, Gs. 63.000, 30 días. Se abrió la confirmación de la opción 1 y se canceló con `2 No` (`Transacción cancelada`).
- **Opción 4 (`ILIMITADOS`)**: Para `0981000000` mostró 6 paquetes: 1) `Internet+Minx10000x1D` (Gs. 10.000, 1 día); 2) `Internet+Minx15000x2D` (Gs. 15.000, 2 días); 3) `Internet+Minx20000x3D` (Gs. 20.000, 3 días); 4) `Internet+Minx25000x4D` (Gs. 25.000, 4 días); 5) `Internet+Minx40000x8D` (Gs. 40.000, 8 días); 6) `TSports+Ilix15000x1D` (Gs. 15.000, 1 día). Se abrió el resumen de la opción 1 y se canceló con `2.NO` (`Transaccion cancelada`).
- **Opción 5 (`Roaming PRE`)**: Para `0981000000` mostró 4 paquetes América: `AMERICAx200MBx10000Gsx1D`, `AMERICAx1GBx30000Gsx7D`, `AMERICAx2GBx50000Gsx15D`, `AMERICAx3GBx60000Gsx30D`. Se canceló antes de seleccionar.
- **Opción 6 (`Saldo`)**: Informa el saldo disponible de Mini Carga.
- **Opción 7 (`PIN`)**: Solicitó `Ingrese el nuevo PIN`; se canceló sin modificar.
- **Opción 8 (`Historial`)**: Muestra las últimas 3 transacciones (ID de transacción, fecha/hora, destino y monto).

## Claro — `*321#`
El código abrió `Prepago CLARO`:
- **Página 1**: `1 Recarga de saldo; 2 Venta de packs; 3 Últimos incentivos; 4 Última recarga; 5 Consulta de Recarga; 6 Próxima página`.
- **Página 2**: `1 Anterior; 2 Informe de ventas; 3 Consultar mi saldo disponible; 4 Cierre de turno; 5 Cambiar PIN; 6 Consulta de montos; 7 Recarga de roaming`.

Detalle verificado:
- **Página 1 — Opción 1 (`Recarga de saldo`)**: Pide `Ingrese Numero de telefono a recargar` (`991000000`), `Ingrese el monto a recargar:` (`1000`), `Ingrese su PIN:` y muestra confirmación final `Confirma Venta de Recarga de $1000 para el numero 991000000? 1 Aceptar / 2 Cancelar`. Se canceló sin confirmar.
- **Página 1 — Opción 2 (`Venta de packs`)**: Pide destino (`991000000`) y luego PIN; en la línea de prueba respondió `La linea no cumple con las condiciones comerciales vigentes. Prestamo Pendiente de Pago o Suspendida/Cancelada. Para mas info el cliente debe llamar *111`.
- **Página 1 — Opción 3 (`Últimos incentivos`)**: Respondió que no se registran premios en los últimos 90 días.
- **Página 1 — Opción 4 (`Última recarga`)**: Muestra detalle (`<ID_TX>`), monto y destino (`99XXXXXXX`).
- **Página 1 — Opción 5 (`Consulta de Recarga`)**: Solicitó PIN y ofreció `1 Consultar por id de transaccion`, `2 Consultar por numero de suscriptor`, `3 Salir`.
- **Página 2 — Opción 2 (`Informe de ventas`)**: Solicitó PIN y fechas desde/hasta (`DDMMAAAA`), respondiendo ventas totales y saldo disponible.
- **Página 2 — Opción 3 (`Consultar mi saldo disponible`)**: Solicita PIN e informa el saldo disponible.
- **Página 2 — Opción 4 (`Cierre de turno`)**: Solicitó `Ingrese su PIN:`; cancelado sin ejecutar cierre.
- **Página 2 — Opción 5 (`Cambiar PIN`)**: Solicitó `Ingrese su PIN existente.`; cancelado sin modificar.
- **Página 2 — Opción 6 (`Consulta de montos`)**: Mostró importes consecutivos desde Gs. 1.000 hasta Gs. 124.000 (paso Gs. 1.000).
- **Página 2 — Opción 7 (`Recarga de roaming`)**: Mostró `1 Argentina` y `2 Uruguay`. Al elegir `1 Argentina`, pidió `Ingrese Numero a recargar:` y validó formato extranjero.

## Reglas de formato de número y códigos directos
- **Tigo**: 10 dígitos con `0` inicial (`0981000000`).
- **Claro**: 9 dígitos sin `0` inicial (`991000000`).
- **Códigos directos**: No marcar `*888*<PIN>*1#` — no está verificado y podría iniciar una recarga.
