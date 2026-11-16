# ADR-0026: El QR del kiosco cambia por turno de clase, no cada 30 s

## Metadata

- **Status:** Accepted
- **Date:** 2026-11-15
- **Deciders:** Equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** [#359](https://github.com/Benji-9/SmartElevate/issues/359)
- **Related ADRs:** Cambia la rotación de 30 s de [ADR-0008](0008-check-in-con-qr-rotativo.md) y [ADR-0020](0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md); [ADR-0017](0017-donde-viven-los-parametros.md), [ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md)

---

## 1. Context and Problem Statement

### Context

El [ADR-0008](0008-check-in-con-qr-rotativo.md) eligió un QR que rota cada ~30 s para que nadie pueda compartir una foto y hacer el check-in sin estar frente al ascensor. El costo es que cada kiosco pide un token nuevo cada 30 s: más tráfico al backend (instancia única en Render) y más chances de que un corte breve de red deje el QR vencido en pantalla.

En las pruebas con personas y en las charlas con usuarios, **compartir una foto del QR para validar el turno de alguien que no está ahí se vio muy poco probable**.

### Problem

¿Vale la pena seguir rotando cada 30 s, o conviene que el QR cambie una vez por turno de clase (mañana, tarde, noche)?

---

## 2. Decision Drivers

- Menos pedidos al backend y menos fallas por cortes de red cortos en la tablet.
- El riesgo de compartir fotos es bajo según las pruebas con usuarios.
- Seguir atado al ascensor y a una reserva propia, con la ventana de check-in de −1 a +2 min.
- Mantener simple el MVP, sin calendario nuevo (el de cuatrimestres sigue pendiente, #218).
- Parámetro técnico: va en `application.yml` ([ADR-0017](0017-donde-viven-los-parametros.md)).

---

## 3. Considered Options

#### Option 1: Mantener 30 s

**Pros:** máxima defensa contra fotos compartidas.
**Cons:** carga y fragilidad de red que no se justifican con el riesgo observado.

#### Option 2: TTL fijo de ~4 h

**Pros:** solo cambia un valor.
**Cons:** los cambios caen en horas arbitrarias, sin relación con el horario de cursada.

#### Option 3: Token atado a franjas fijas de turno (elegida)

El token vence en el próximo cambio de turno del campus (mañana, tarde, noche).

**Pros:** el QR cambia cuando cambia la gente en el campus; en un turno el kiosco recibe siempre el mismo token.
**Cons:** hay que configurar las horas de cambio de turno.

---

## 4. Decision Outcome

**Chosen Option:** Option 3.

- `exp` del token es la próxima hora de cambio de turno en `America/Argentina/Buenos_Aires` ([ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md)). Las horas son `app.kiosk.qr-shift-boundaries` (override `APP_KIOSK_QR_SHIFT_BOUNDARIES`, `HH:mm` separadas por coma). Default `07:00,13:00,18:00,23:00`: mañana 07–13, tarde 13–18, noche 18–23; de 23 a 07 el token vale hasta las 07.
- Reemplaza a `app.kiosk.qr-ttl` (`APP_KIOSK_QR_TTL`, 30 s), que se elimina.
- La validación da **1 minuto de tolerancia** pasado el `exp` (constante), para el QR que cambia mientras alguien apunta la cámara. Ya no se acepta "el ciclo anterior" completo.
- El kiosco pide el siguiente token al llegar a `expiresAt`, como hasta ahora; no cambia el contrato de la API.
- Se mantiene todo lo demás: el token firma `elevatorId`, `kioskId` y `exp`; escaneo logueado; reserva propia en ese ascensor; ventana de −1 a +2 min; un check-in por reserva; revocar un kiosco invalida su QR en el acto.

---

## 5. Consequences

### Positive Consequences

- Casi no hay pedidos de token (unos pocos por día y kiosco) y un corte de red corto ya no deja el QR vencido.
- Un kiosco que se reinicia recibe el mismo token del turno.

### Negative Consequences

- **Una foto del QR sirve todo el turno.** Lo que sigue limitando el abuso: hace falta una reserva propia en ese ascensor, estar en la ventana de −1 a +2 min y hay un solo check-in por reserva; pero ya no se prueba que la persona esté frente al ascensor. Aceptado por el riesgo bajo visto con usuarios.
- Rotar `APP_KIOSK_QR_SECRET` invalida los QR en pantalla hasta el próximo cambio de turno (antes se arreglaba en 30 s). Tras rotar la clave, recargar los kioscos.
- Si cambian los horarios de cursada hay que cambiar la variable (redeploy). Con el calendario de cuatrimestres (#218) se podría mover a `app_config`.

### Technical Implications

- `KioskProperties.qrShiftBoundaries` (lista de `HH:mm`, validada al arrancar) en lugar de `qrTtl`; `QrTokenService` usa `CampusTime`.
- Sin migraciones ni cambios de endpoints (`docs/openapi.json` solo cambia el texto de una descripción).
- El tope de pedidos por kiosco (10 por minuto) sobra con el nuevo ritmo.

---

## 6. Implementation and Validation

- [x] Implementado en [#359](https://github.com/Benji-9/SmartElevate/issues/359).
- [ ] Si en el piloto aparecen check-ins desde fuera del hall, volver a acortar el vencimiento (por ejemplo, agregando horas a `qr-shift-boundaries`).

---

## 7. References

- [Check-in con QR](../reglas/check-in-qr.md), [ADR-0008](0008-check-in-con-qr-rotativo.md), [ADR-0020](0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md).
