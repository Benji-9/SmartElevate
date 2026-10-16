# 0016. QR firmado con HMAC-SHA256 y kiosco web en el hall

- **Estado:** Aceptado; refina el ADR 0008
- **Fecha:** 2026-10-14
- **Issues:** [#23](https://github.com/Benji-9/SmartElevate/issues/23), [#219](https://github.com/Benji-9/SmartElevate/issues/219). Completa el [ADR 0008](0008-check-in-con-qr-rotativo.md) (decisiones de reglas en [#113](https://github.com/Benji-9/SmartElevate/issues/113)).
- **Autores:** @Benji-9

## Contexto

El [ADR 0008](0008-check-in-con-qr-rotativo.md) eligió un QR rotativo firmado y atado al ascensor, pero dejó abiertos dos puntos: qué dispositivo lo muestra ([#23](https://github.com/Benji-9/SmartElevate/issues/23)) y cómo se firma el token. No hay presupuesto para hardware dedicado ni acceso a la infraestructura del edificio, y el backend corre en una sola instancia de Render.

## Opciones consideradas

Dispositivo:

1. **Pantalla o lector dedicado por ascensor** (Raspberry Pi, display): robusto, pero cuesta plata, hay que instalarlo y mantenerlo.
2. **Kiosco web** `/qr/:ascensor` abierto en una tablet o pantalla del hall: sin hardware propio, se prueba con cualquier navegador. Depende de que haya una pantalla prendida y con red.
3. **QR impreso fijo**: gratis, pero se puede fotografiar y compartir (lo descartó el ADR 0008).

Firma:

- **a. JWT firmado** con el mismo stack de los access tokens: estándar, pero el token queda largo y el QR más denso, difícil de leer de lejos.
- **b. Token propio `elevatorId + exp` con HMAC-SHA256**: corto, sin estado en el servidor y fácil de validar.
- **c. Token aleatorio guardado en la base**: revocable, pero suma una escritura por rotación y por ascensor.

## Decisión

Opción **2** con firma **b**:

- El kiosco es una página del frontend, `/qr/:ascensor`, pensada para una tablet o pantalla en el hall de cada ascensor. Pide el token al backend y lo muestra como QR.
- El token lleva **`elevatorId` y `exp`**, firmados con **HMAC-SHA256**. La clave sale de una **variable de entorno** del backend, distinta de `JWT_SECRET`, y nunca va al repo.
- El QR **rota cada ~30 s** (parámetro `qr_rotation` de `app_config`).
- Lo escanea **la persona con su celular**, logueada: el JWT dice quién es y el QR solo aporta el ascensor y el momento, como dice el ADR 0008.

## Consecuencias

- No hace falta comprar hardware: alcanza con una tablet o pantalla prestada por ascensor, y para la demo con una laptop.
- Si el kiosco se queda sin red o se apaga, no hay check-in en ese ascensor. Queda el ingreso manual del código (`/check-in/codigo`).
- Hay que cargar la clave HMAC en Render cuando se implemente (README, "Setup manual pendiente"). Rotarla invalida los QR en pantalla, que se renuevan solos en la rotación siguiente.
- `qr_rotation` vale hoy **PT60S** (`V3`, [#113](https://github.com/Benji-9/SmartElevate/issues/113)): bajarlo a 30 s es un cambio de valor que va con la implementación ([#219](https://github.com/Benji-9/SmartElevate/issues/219)).
- Falta definir en #219 cómo se protege el endpoint que entrega el token al kiosco, para que nadie lo pida desde su casa y se ahorre ir al ascensor.
