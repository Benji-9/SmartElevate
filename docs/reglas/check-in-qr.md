# Check-in con QR

> Estado: **aprobado**. Decisión de diseño en los ADR [0008](../adr/0008-check-in-con-qr-rotativo.md) y [0016](../adr/0016-qr-firmado-con-hmac-y-kiosco-web.md) (firma y kiosco). Se implementa en [#219](https://github.com/Benji-9/SmartElevate/issues/219).

El check-in confirma que el usuario usó su reserva. Alimenta el no-show y los KPIs.

## Cómo funciona

1. Cada ascensor muestra un **QR con un token firmado** que **rota cada ~30 s** y está **atado a ese ascensor**. Se muestra en un **kiosco web** (`/qr/:ascensor`) abierto en una tablet o pantalla del hall, sin hardware propio ([#23](https://github.com/Benji-9/SmartElevate/issues/23)). La rotación es un parámetro técnico, `app.kiosk.qr-ttl` en `application.yml` (30 s, override por `APP_KIOSK_QR_TTL`); dejó de estar en `app_config` (`V15`, [#229](https://github.com/Benji-9/SmartElevate/issues/229)).
2. El usuario lo escanea desde la app. El **JWT de su sesión identifica quién escanea**; el QR solo identifica el ascensor y el momento.
3. El backend valida la firma, que el token no esté vencido y que corresponda a una reserva activa del usuario.

## Reglas

- **Una sola lectura por reserva.**
- Ventana aceptada: desde **1 min antes** hasta **2 min después** de la salida.

## Resultados

| Resultado | Cuándo | ¿No-show? |
| --- | --- | --- |
| **Cumplida** | Ascensor correcto, entre −1 min y +60 s | No |
| **Otro ascensor** | Ascensor distinto al de la reserva, dentro de la ventana | No, pero se registra (KPI de distribución) |
| **Fuera de hora** | Entre +60 s y +2 min | Sí, pero queda el registro de que vino |
| *(sin escaneo)* | Nada hasta +2 min | Sí |

La fila *fuera de hora* reconcilia dos reglas: el no-show se marca a los 60 s ([turnos](turnos.md#5-no-show)), pero el QR se acepta hasta +2 min. Decidido en [#113](https://github.com/Benji-9/SmartElevate/issues/113).

## Seguridad

- La rotación corta evita que se comparta una foto del QR.
- La firma **HMAC-SHA256** de `elevatorId + exp`, con una clave del servidor en variable de entorno, evita fabricar tokens.
- La unicidad por reserva evita que un mismo escaneo cuente dos veces.
