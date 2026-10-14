# 0020. Autenticación de kioscos por credencial de dispositivo

- **Estado:** Propuesto
- **Fecha:** 2026-10-14
- **Issues:** [#229](https://github.com/Benji-9/SmartElevate/issues/229), parte de [#219](https://github.com/Benji-9/SmartElevate/issues/219). Complementa los ADR [0008](0008-check-in-con-qr-rotativo.md) y [0016](0016-qr-firmado-con-hmac-y-kiosco-web.md).
- **Autores:** @Benji-9

## Contexto

El [ADR 0016](0016-qr-firmado-con-hmac-y-kiosco-web.md) dejó abierto cómo se protege el endpoint que le da el token del QR al kiosco. Si fuera público, cualquiera podría pedir desde su casa el token de un ascensor y hacer check-in sin ir. El kiosco es una página web en una tablet del hall: no hay una persona logueada, y una sesión de usuario guardada en la tablet le daría a quien la agarre los permisos de esa persona.

## Opciones consideradas

1. **Endpoint público con el ascensor como parámetro**: trivial, pero no protege nada.
2. **Sesión de un usuario ADMIN abierta en el kiosco**: reusa el login, pero deja una sesión con todos los permisos en una pantalla pública, que además vence.
3. **Credencial de dispositivo por kiosco**: un secreto aleatorio que se genera en el alta, atado a un ascensor y revocable de a uno.

## Decisión

Opción **3**:

- Un ADMIN da de alta el kiosco para un ascensor (`POST /api/admin/kiosks`). El secreto sale de un CSPRNG (32 bytes), la respuesta lo muestra **una sola vez** y en la base queda solo su **SHA-256**, como con los refresh tokens. Revocar (`POST /api/admin/kiosks/{id}/revoke`) corta el kiosco en el acto. Las altas y revocaciones quedan en la auditoría de administración (`priority_audit`, extendida en `V15` con `target_kiosk_id`).
- El kiosco pide el token con `GET /api/kiosks/me/qr-token` y `Authorization: Bearer <secreto>`. Esa ruta tiene **su propia cadena de seguridad**: el resource server JWT del resto de `/api` rechazaría el secreto como un JWT inválido.
- El **ascensor sale de la credencial**, nunca de un parámetro. El token firma `elevatorId`, `kioskId` y `exp` con HMAC-SHA256 (la clave `APP_KIOSK_QR_SECRET` es distinta de `JWT_SECRET` y de las credenciales). El `kioskId` firmado permite saber, sin confiar en el cliente, si el check-in vino de un kiosco real o del simulador de admin.
- La rotación es de **30 s**, con ciclos alineados al reloj, y la validación acepta además el **ciclo anterior**. Pasa de `qr_rotation` (`app_config`) a `app.kiosk.qr-ttl` en `application.yml`: es el TTL de un token firmado, un parámetro técnico ([ADR 0017](0017-donde-viven-los-parametros.md)). Esto reemplaza lo que decía el ADR 0016 sobre `qr_rotation`.
- Cada kiosco tiene un tope de pedidos (10 por minuto, en `application.yml`), en memoria porque hay una sola instancia.
- Los kioscos simulados solo se pueden crear con `APP_KIOSK_SIMULATOR_ENABLED=true` (apagado por default).

## Consecuencias

- Una tablet robada o un secreto filtrado se resuelve revocando ese kiosco y dando de alta otro, sin tocar a los demás ni la clave HMAC.
- El secreto se carga a mano en el kiosco una vez (#232). Si se pierde, no se recupera: se crea otro kiosco.
- **Riesgo residual aceptado para el MVP:** un QR a la vista se puede fotografiar y reenviar a alguien que no está en el ascensor, y vale mientras dure el ciclo y su tolerancia (~30 a 60 s). La rotación corta lo acota, y como cada reserva acepta una sola lectura, el mismo escaneo no cuenta dos veces. A futuro se podría pedir la geolocalización del celular o una señal Bluetooth del kiosco.
- Con más de una instancia del backend, el tope de pedidos se multiplicaría por la cantidad de instancias: habría que pasarlo a la base.
