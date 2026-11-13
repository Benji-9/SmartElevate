# ADR-0008: Check-in con QR rotativo firmado

## Metadata

- **Status:** Accepted
- **Date:** 2026-09-28
- **Deciders:** Equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** [#7](https://github.com/Benji-9/SmartElevate/issues/7), [#23](https://github.com/Benji-9/SmartElevate/issues/23), [#219](https://github.com/Benji-9/SmartElevate/issues/219), [#238](https://github.com/Benji-9/SmartElevate/issues/238), [#113](https://github.com/Benji-9/SmartElevate/issues/113) (decisiones de reglas), [#230](https://github.com/Benji-9/SmartElevate/issues/230) (token implementado)
- **Related ADRs:** Absorbe ADR-0016 (QR firmado con HMAC-SHA256 y kiosco web, 2026-10-14) y ADR-0021 (check-in sin ingreso manual, 2026-10-14); [ADR-0017](0017-donde-viven-los-parametros.md); [ADR-0020](0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md) (reemplaza parte de lo que decía el ex 0016 sobre `qr_rotation`)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0008-check-in-con-qr-rotativo.md`)

---

## 1. Context and Problem Statement

### Context

Para detectar no-shows y medir KPIs hay que saber si el usuario efectivamente usó su reserva y en qué ascensor.

Un QR rotativo firmado y atado al ascensor deja abiertos tres puntos: qué dispositivo lo muestra ([#23](https://github.com/Benji-9/SmartElevate/issues/23)), cómo se firma el token y qué pasa si el dispositivo falla. No hay presupuesto para hardware dedicado ni acceso a la infraestructura del edificio, y el backend corre en una sola instancia de Render.

### Problem

¿Cómo comprobamos que la persona estuvo frente al ascensor de su reserva, sin hardware propio, con un token que no se pueda falsear ni reutilizar desde afuera, y qué respaldo damos si el dispositivo que muestra el QR falla?

---

## 2. Decision Drivers

- Que haga falta estar físicamente frente al ascensor (un "llegué" trivial de falsear no sirve).
- Sin presupuesto para hardware dedicado ni acceso a la infraestructura del edificio.
- Token corto (QR legible de lejos), sin estado en el servidor y fácil de validar.
- Una sola instancia del backend en Render.
- Que se pueda probar con cualquier navegador en la demo.
- Mantener simple el MVP: no sumar secretos extra que firmar, validar y limitar contra fuerza bruta.

---

## 3. Considered Options

### Cómo se prueba la presencia

#### Option 1: Botón "llegué" en la app

**Cons:**
- Trivial de falsear desde cualquier lugar.

#### Option 2: QR fijo impreso en cada ascensor

**Pros:**
- Gratis (también aplica a su variante del ADR-0016, QR impreso fijo).

**Cons:**
- Se puede fotografiar y compartir.

#### Option 3: QR con token firmado que rota cada 30–60 s

Atado al ascensor, escaneado con la sesión (JWT) del usuario.

**Pros:**
- Hay que estar frente al ascensor.

**Cons:**
- Necesita una pantalla por ascensor.

#### Option 4: NFC / BLE

**Pros:**
- Más robusto.

**Cons:**
- Requiere hardware y soporte en el celular.

### Qué dispositivo muestra el QR (Option 3)

#### Dispositivo 1: Pantalla o lector dedicado por ascensor (Raspberry Pi, display)

**Pros:**
- Robusto.

**Cons:**
- Cuesta plata; hay que instalarlo y mantenerlo.

#### Dispositivo 2: Kiosco web `/qr/:ascensor` en una tablet o pantalla del hall

**Pros:**
- Sin hardware propio; se prueba con cualquier navegador.

**Cons:**
- Depende de que haya una pantalla prendida y con red.

#### Dispositivo 3: QR impreso fijo

**Cons:**
- Se puede fotografiar y compartir (ya descartado arriba como Option 2).

### Cómo se firma el token

#### Firma a: JWT firmado

Con el mismo stack de los access tokens.

**Pros:**
- Estándar.

**Cons:**
- El token queda largo y el QR más denso, difícil de leer de lejos.

#### Firma b: Token propio `elevatorId + exp` con HMAC-SHA256

**Pros:**
- Corto, sin estado en el servidor y fácil de validar.

#### Firma c: Token aleatorio guardado en la base

**Pros:**
- Revocable.

**Cons:**
- Suma una escritura por rotación y por ascensor.

### Respaldo si el kiosco falla

#### Respaldo 1: Código corto aparte (p. ej. 6 caracteres, rotando con el QR)

**Pros:**
- Se puede tipear.

**Cons:**
- Es otro secreto que firmar, validar y limitar contra fuerza bruta; tampoco existe si el kiosco está caído.

#### Respaldo 2: Tipear el token completo

**Cons:**
- No es usable.

#### Respaldo 3: Sacar el ingreso manual del MVP

**Pros:**
- Nada más que mantener.

**Cons:**
- Sin respaldo: la caída de un kiosco se acepta como limitación.

---

## 4. Decision Outcome

**Chosen Option:** QR rotativo firmado (Option 3), mostrado por un kiosco web (dispositivo 2), firmado con HMAC-SHA256 (firma b) y sin ingreso manual en el MVP (respaldo 3).

### Rationale

- **Check-in (decisión original, 2026-09-28):** lectura única por reserva, en la ventana de −1 min a +2 min. Resultados: *cumplida*, *otro ascensor* o *fuera de hora*. El JWT identifica a quien escanea; el QR solo aporta el ascensor y el momento. Detalle: [check-in](../reglas/check-in-qr.md).
- **Kiosco y firma (ex ADR-0016, 2026-10-14):**
  - El kiosco es una página del frontend, `/qr/:ascensor`, pensada para una tablet o pantalla en el hall de cada ascensor. Pide el token al backend y lo muestra como QR.
  - El token lleva **`elevatorId` y `exp`**, firmados con **HMAC-SHA256**. La clave sale de una **variable de entorno** del backend, distinta de `JWT_SECRET`, y nunca va al repo.
  - El QR **rota cada ~30 s**.
  - Lo escanea **la persona con su celular**, logueada.
  - Nota posterior: [ADR-0020](0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md) reemplazó lo que el ex 0016 decía sobre el parámetro `qr_rotation` de `app_config`: la rotación es de 30 s y pasa a `app.kiosk.qr-ttl` en `application.yml` (parámetro técnico, [ADR-0017](0017-donde-viven-los-parametros.md)). El token pasó a firmar también `kioskId`, y se agregó la autenticación del kiosco por credencial de dispositivo.
- **Sin ingreso manual (ex ADR-0021, decisión del equipo, 14/10):** con el token ya implementado ([#230](https://github.com/Benji-9/SmartElevate/issues/230)), lo que se escanea es `base64(elevatorId|kioskId|exp).firma`: unos 80 caracteres opacos que rotan cada ~30 s. Nadie puede tipearlo, y el respaldo (`/check-in/codigo`, que había dejado el ex 0016) tampoco sirve en el caso que tenía que cubrir: sin red, el kiosco no tiene token para mostrar. El check-in del MVP es solo escaneando el QR del kiosco; si el kiosco se queda sin red o se apaga, en ese ascensor no hay check-in mientras dure la caída.

Respuesta a los drivers: la rotación y el ascensor dentro del token obligan a estar frente al ascensor; HMAC da un token corto y sin estado (a diferencia de JWT o de un token en base); el kiosco web evita hardware propio; y no tener ingreso manual evita un segundo secreto sin cubrir el caso real de falla.

---

## 5. Consequences

### Positive Consequences

- No hace falta comprar hardware: alcanza con una tablet o pantalla prestada por ascensor, y para la demo con una laptop o una página web abierta en cualquier pantalla.
- Token corto, sin estado en el servidor.

### Negative Consequences

- Depende de un **dispositivo que muestre el QR** en cada ascensor ([#23](https://github.com/Benji-9/SmartElevate/issues/23)).
- Si el kiosco se queda sin red o se apaga, no hay check-in en ese ascensor mientras dure la caída. Eso suma no-shows que no son culpa del usuario. Si pasa seguido, la Fase 2 puede sumar un **código corto** (respaldo 1) o que el admin anule las faltas de esa franja.
- Sin cámara o sin permiso, la app explica cómo habilitarla; no ofrece otra vía.
- Propuesta a confirmar: entre +60 s y +2 min el resultado es *fuera de hora* y cuenta como no-show.

### Technical Implications

- Hace falta un secreto de firma en el servidor, configurado por variable de entorno (hoy `APP_KIOSK_QR_SECRET`, ver [ADR-0020](0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)); cargarlo en Render (README, "Setup manual pendiente"). Rotarlo invalida los QR en pantalla, que se renuevan solos en la rotación siguiente.
- `qr_rotation` valía **PT60S** en `V3` ([#113](https://github.com/Benji-9/SmartElevate/issues/113)); con [ADR-0020](0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md) la rotación pasó a 30 s en `app.kiosk.qr-ttl` ([#219](https://github.com/Benji-9/SmartElevate/issues/219)).
- El endpoint que entrega el token al kiosco se protege con credencial de dispositivo ([ADR-0020](0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)), para que nadie lo pida desde su casa y se ahorre ir al ascensor.
- Se borran `/check-in/codigo`, el botón "Ingresar código manualmente" y sus tests. El ingreso manual pasa a "Fuera del MVP" en [alcance-mvp.md](../alcance-mvp.md).
- Para que el QR no quede atado al ascensor equivocado, `GET /api/kiosks/me/qr-token` devuelve `elevatorId` y el kiosco no muestra el QR si no coincide con el de su URL.

---

## 6. Implementation and Validation

- [x] Token HMAC-SHA256 implementado ([#230](https://github.com/Benji-9/SmartElevate/issues/230)).
- [x] Kiosco web `/qr/:ascensor` y endpoint `GET /api/kiosks/me/qr-token` con chequeo de `elevatorId`.
- [x] Ingreso manual eliminado (`/check-in/codigo`, botón y tests) ([#238](https://github.com/Benji-9/SmartElevate/issues/238)).
- [x] Ingreso manual movido a "Fuera del MVP" en [alcance-mvp.md](../alcance-mvp.md).
- [ ] Confirmar la propuesta: entre +60 s y +2 min el resultado es *fuera de hora* y cuenta como no-show.
- [ ] Fase 2, si las caídas de kiosco son frecuentes: código corto o anulación de faltas por el admin.

---

## 7. References

- Reglas: [check-in con QR](../reglas/check-in-qr.md), [alcance del MVP](../alcance-mvp.md).
- Issues: [#7](https://github.com/Benji-9/SmartElevate/issues/7), [#23](https://github.com/Benji-9/SmartElevate/issues/23), [#113](https://github.com/Benji-9/SmartElevate/issues/113), [#219](https://github.com/Benji-9/SmartElevate/issues/219), [#230](https://github.com/Benji-9/SmartElevate/issues/230), [#238](https://github.com/Benji-9/SmartElevate/issues/238).
- ADRs: [ADR-0017](0017-donde-viven-los-parametros.md), [ADR-0020](0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md).
