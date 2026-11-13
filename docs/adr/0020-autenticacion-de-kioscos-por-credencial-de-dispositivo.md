# ADR-0020: Autenticación de kioscos por credencial de dispositivo

## Metadata

- **Status:** Accepted
- **Date:** 2026-10-14
- **Deciders:** Equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** [#229](https://github.com/Benji-9/SmartElevate/issues/229), parte de [#219](https://github.com/Benji-9/SmartElevate/issues/219); [#232](https://github.com/Benji-9/SmartElevate/issues/232) (carga manual del secreto)
- **Related ADRs:** Complementa [ADR-0008](0008-check-in-con-qr-rotativo.md) (que absorbió al ex ADR-0016, cuya parte sobre `qr_rotation` este ADR reemplaza); [ADR-0017](0017-donde-viven-los-parametros.md)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md`)

---

## 1. Context and Problem Statement

### Context

El [ADR-0008](0008-check-in-con-qr-rotativo.md) (ex 0016) dejó abierto cómo se protege el endpoint que le da el token del QR al kiosco. El kiosco es una página web en una tablet del hall: no hay una persona logueada, y una sesión de usuario guardada en la tablet le daría a quien la agarre los permisos de esa persona.

### Problem

Si el endpoint fuera público, cualquiera podría pedir desde su casa el token de un ascensor y hacer check-in sin ir. ¿Cómo autenticamos al kiosco sin una persona logueada y sin dejar una sesión con permisos en una pantalla pública?

---

## 2. Decision Drivers

- Que nadie pueda obtener el token de un ascensor sin estar en el kiosco.
- No dejar sesiones de usuario con permisos en una pantalla pública (además vencen).
- Poder revocar un kiosco de a uno, en el acto, sin tocar a los demás.
- Que el ascensor no dependa de lo que mande el cliente.
- Una sola instancia del backend (límites en memoria aceptables).

---

## 3. Considered Options

### Option A: Endpoint público con el ascensor como parámetro

**Pros:**
- Trivial.

**Cons:**
- No protege nada.

### Option B: Sesión de un usuario ADMIN abierta en el kiosco

**Pros:**
- Reusa el login.

**Cons:**
- Deja una sesión con todos los permisos en una pantalla pública, que además vence.

### Option C: Credencial de dispositivo por kiosco

Un secreto aleatorio que se genera en el alta, atado a un ascensor y revocable de a uno.

**Pros:**
- Revocable de a uno, sin permisos de usuario, el ascensor sale de la credencial.

**Cons:**
- El secreto se carga a mano en el kiosco y no se recupera si se pierde.

---

## 4. Decision Outcome

**Chosen Option:** Option C, credencial de dispositivo por kiosco.

### Rationale

- Un ADMIN da de alta el kiosco para un ascensor (`POST /api/admin/kiosks`). El secreto sale de un CSPRNG (32 bytes), la respuesta lo muestra **una sola vez** y en la base queda solo su **SHA-256**, como con los refresh tokens. Revocar (`POST /api/admin/kiosks/{id}/revoke`) corta el kiosco en el acto. Las altas y revocaciones quedan en la auditoría de administración (`priority_audit`, extendida en `V15` con `target_kiosk_id`).
- El kiosco pide el token con `GET /api/kiosks/me/qr-token` y `Authorization: Bearer <secreto>`. Esa ruta tiene **su propia cadena de seguridad**: el resource server JWT del resto de `/api` rechazaría el secreto como un JWT inválido.
- El **ascensor sale de la credencial**, nunca de un parámetro. El token firma `elevatorId`, `kioskId` y `exp` con HMAC-SHA256 (la clave `APP_KIOSK_QR_SECRET` es distinta de `JWT_SECRET` y de las credenciales). El `kioskId` firmado permite saber, sin confiar en el cliente, si el check-in vino de un kiosco real o del simulador de admin.
- La rotación es de **30 s**, con ciclos alineados al reloj, y la validación acepta además el **ciclo anterior**. Pasa de `qr_rotation` (`app_config`) a `app.kiosk.qr-ttl` en `application.yml`: es el TTL de un token firmado, un parámetro técnico ([ADR-0017](0017-donde-viven-los-parametros.md)). Esto reemplaza lo que decía el ex ADR-0016 sobre `qr_rotation`.
- Cada kiosco tiene un tope de pedidos (10 por minuto, en `application.yml`), en memoria porque hay una sola instancia.
- Los kioscos simulados solo se pueden crear con `APP_KIOSK_SIMULATOR_ENABLED=true` (apagado por default).

---

## 5. Consequences

### Positive Consequences

- Una tablet robada o un secreto filtrado se resuelve revocando ese kiosco y dando de alta otro, sin tocar a los demás ni la clave HMAC.

### Negative Consequences

- El secreto se carga a mano en el kiosco una vez ([#232](https://github.com/Benji-9/SmartElevate/issues/232)). Si se pierde, no se recupera: se crea otro kiosco.
- **Riesgo residual aceptado para el MVP:** un QR a la vista se puede fotografiar y reenviar a alguien que no está en el ascensor, y vale mientras dure el ciclo y su tolerancia (~30 a 60 s). La rotación corta lo acota, y como cada reserva acepta una sola lectura, el mismo escaneo no cuenta dos veces. A futuro se podría pedir la geolocalización del celular o una señal Bluetooth del kiosco.
- Con más de una instancia del backend, el tope de pedidos se multiplicaría por la cantidad de instancias: habría que pasarlo a la base.

### Technical Implications

- Endpoints nuevos: `POST /api/admin/kiosks`, `POST /api/admin/kiosks/{id}/revoke`, `GET /api/kiosks/me/qr-token` (con cadena de seguridad propia).
- Migración `V15`: `target_kiosk_id` en `priority_audit`.
- Variables de entorno: `APP_KIOSK_QR_SECRET`, `APP_KIOSK_SIMULATOR_ENABLED`; propiedades `app.kiosk.qr-ttl` y el tope de pedidos en `application.yml`.

---

## 6. Implementation and Validation

- [x] Alta y revocación de kioscos por ADMIN, con secreto mostrado una sola vez y SHA-256 en la base ([#229](https://github.com/Benji-9/SmartElevate/issues/229)).
- [x] Cadena de seguridad propia para `/api/kiosks/me/**`.
- [x] Token HMAC-SHA256 con `elevatorId`, `kioskId` y `exp`, ciclo de 30 s y tolerancia de un ciclo.
- [x] Tope de 10 pedidos por minuto por kiosco.
- [x] Simulador de kioscos detrás de `APP_KIOSK_SIMULATOR_ENABLED`.
- [ ] Si hay más de una instancia del backend: mover el tope de pedidos a la base.

---

## 7. References

- Issues: [#229](https://github.com/Benji-9/SmartElevate/issues/229), [#219](https://github.com/Benji-9/SmartElevate/issues/219), [#232](https://github.com/Benji-9/SmartElevate/issues/232).
- ADRs: [ADR-0008](0008-check-in-con-qr-rotativo.md), [ADR-0017](0017-donde-viven-los-parametros.md).
