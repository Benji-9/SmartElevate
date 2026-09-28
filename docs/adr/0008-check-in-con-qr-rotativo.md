# 0008. Check-in con QR rotativo firmado

- **Estado:** Propuesto
- **Fecha:** 2026-09-28
- **Issues:** [#7](https://github.com/Benji-9/SmartElevate/issues/7), [#23](https://github.com/Benji-9/SmartElevate/issues/23)

## Contexto

Para detectar no-shows y medir KPIs hay que saber si el usuario efectivamente usó su reserva y en qué ascensor.

## Opciones consideradas

1. **Botón "llegué" en la app**: trivial de falsear desde cualquier lugar.
2. **QR fijo impreso en cada ascensor**: se puede fotografiar y compartir.
3. **QR con token firmado que rota cada 30–60 s**, atado al ascensor, escaneado con la sesión (JWT) del usuario: hay que estar frente al ascensor, pero necesita una pantalla por ascensor.
4. **NFC / BLE**: más robusto, requiere hardware y soporte en el celular.

## Decisión

Opción **3**:

- Lectura única por reserva, en la ventana de −1 min a +2 min.
- Resultados: *cumplida*, *otro ascensor* o *fuera de hora*.
- El JWT identifica a quien escanea; el QR solo aporta el ascensor y el momento.

Detalle: [check-in](../reglas/check-in-qr.md).

## Consecuencias

- Depende de un **dispositivo que muestre el QR** en cada ascensor ([#23](https://github.com/Benji-9/SmartElevate/issues/23)). Para la demo alcanza con una página web abierta en cualquier pantalla.
- Hace falta un secreto de firma en el servidor, configurado por variable de entorno.
- Propuesta a confirmar: entre +60 s y +2 min el resultado es *fuera de hora* y cuenta como no-show.
