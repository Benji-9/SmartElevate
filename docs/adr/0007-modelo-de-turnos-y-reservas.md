# ADR-0007: Modelo de turnos: la franja es una salida de ascensor

## Metadata

- **Status:** Accepted
- **Date:** 2026-09-28 (recorte de alcance del MVP incorporado el 2026-10-14, ex ADR 0018)
- **Deciders:** Equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** [#7](https://github.com/Benji-9/SmartElevate/issues/7), [#113](https://github.com/Benji-9/SmartElevate/issues/113), [#218](https://github.com/Benji-9/SmartElevate/issues/218), [#212](https://github.com/Benji-9/SmartElevate/issues/212), [#213](https://github.com/Benji-9/SmartElevate/issues/213), [#134](https://github.com/Benji-9/SmartElevate/issues/134)
- **Related ADRs:** [ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md). Absorbe ADR-0018 (alcance del MVP en las reglas de turnos).
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0007-modelo-de-turnos-y-reservas.md`; el ADR 0018 absorbido: `git show d5a37be:docs/adr/0018-alcance-del-mvp-en-reglas-de-turnos.md`)

---

## 1. Context and Problem Statement

### Context

Hay que definir qué es un "turno", cuánto dura y cómo se reparte el cupo de 10 personas entre usuarios comunes y prioritarios. Según las mediciones del equipo (14 s para que suban 10 personas, 3 s por piso), un viaje ida y vuelta al piso 10 tarda ~1,5 min. La encuesta muestra que el 43,5 % espera entre 5 y 10 min.

El modelo inicial incluía cancelación libre hasta 1 min antes, lista de espera, envejecimiento de la prioridad y edificio alternativo por BFS. Con la reserva, la cancelación y el cupo prioritario ya implementados, el equipo revisó el 14/10 qué entra en el MVP: varias de esas piezas dependen de datos que todavía no hay (check-in, espera real medida) y no llegan a probarse bien antes de la entrega.

### Problem

¿Qué es una franja, cómo se reparte el cupo entre comunes y prioritarios, y qué parte del modelo completo entra en el MVP?

---

## 2. Decision Drivers

- Reflejar la capacidad real del ascensor (14 s por 10 personas, 3 s por piso, ~1,5 min ida y vuelta al piso 10).
- Que la espera estimada tenga sentido para el usuario (43,5 % espera entre 5 y 10 min).
- Garantizar cupo a los prioritarios sin dejar asientos vacíos.
- Reglas fáciles de explicar y de probar.
- Poder probar y defender cada regla antes de la entrega: no incluir piezas que no se pueden calibrar sin check-in ni mediciones.

---

## 3. Considered Options

### Franja y cupo

#### Option A: Franjas de 15 min con cupo 10

Fáciles de entender.

**Pros:**
- Simples de explicar.

**Cons:**
- El ascensor hace ~10 viajes por franja y la app reservaría una fracción mínima de su capacidad real.

#### Option B: Franja = una salida de ascensor (~2 min)

**Pros:**
- Refleja la capacidad real (~300 lugares/hora por ascensor).
- La espera estimada tiene sentido.

#### Option C: Cola sin franjas (turno por orden de llegada)

**Pros:**
- No hace falta modelar horarios.

**Cons:**
- No permite planificar ni reservar.

### Prioritarios

#### Option a: Cola prioritaria pura

**Cons:**
- Más difícil de explicar a los usuarios y de probar.

#### Option b: Ascensor dedicado + hasta 2 de 10 lugares reservados en los comunes, liberados si nadie los usa

**Pros:**
- Cupo garantizado sin asientos vacíos.

### Alcance del MVP (revisión del 14/10)

#### Option X: Mantener todo el modelo original

**Pros:**
- Modelo completo.

**Cons:**
- Incluye piezas que no se pueden calibrar sin check-in ni mediciones, y deja menos tiempo para probar lo central.

#### Option Y: Recortar al núcleo (reservar, cancelar, cupo prioritario, check-in) y pasar el resto a una Fase 2

**Pros:**
- Cada regla que queda se puede probar y defender.

**Cons:**
- Menos funcionalidad.

---

## 4. Decision Outcome

**Chosen Option:** franja = salida de ascensor (B), prioritarios con ascensor dedicado + hasta 2 de 10 lugares reservados (b), y MVP recortado al núcleo (Y).

### Rationale

B refleja la capacidad real y b garantiza cupo sin asientos vacíos. Las reglas son:

- Ventana de reserva de 30 a 2 min antes de la salida, solo del mismo día.
- 1 reserva activa por usuario y tope diario.
- No-show con penalización leve: 3 en 7 días → 24 h sin reservar, sin suspender a prioritarios.
- Orden de asignación movilidad reducida > docentes > piso más alto > llegada.

Con el recorte del 14/10 (Y), cambia respecto de la versión inicial:

- **Cancelar sin falta hasta 10 min antes** de la salida (antes, 1 min), calibrable en el piloto. Compromiso aceptado: como la reserva abre 30 min y cierra 2 min antes, quien reserva en los últimos 10 min no puede cancelar sin falta.
- Cancelar sin falta **devuelve el cupo diario**; la cancelación tardía no. Después de la salida **no se puede cancelar**: el turno queda como no-show, o como usado si hubo check-in.
- **Fuera del MVP (Fase 2):** lista de espera (ordenada por prioridad, para reasignar lugares liberados después del cierre de la ventana), envejecimiento ([#212](https://github.com/Benji-9/SmartElevate/issues/212)), edificio alternativo (calculado con BFS sobre un grafo de conexiones configurable, [#213](https://github.com/Benji-9/SmartElevate/issues/213); a lo sumo un aviso informativo) y la tolerancia X de la cancelación.
- **Ascensor fuera de servicio** con reservas activas ([#134](https://github.com/Benji-9/SmartElevate/issues/134)): se cancelan solas, sin falta, se devuelve el cupo y se avisa a cada persona. Si otro ascensor tiene lugar, se le ofrece reubicarse; no se la mueve sola.

Lo demás sigue igual: franja de ~2 min, ventana de 30 a 2 min, 1 reserva activa, tope diario, no-show y cupo prioritario. Detalle y el porqué de cada recorte en [alcance del MVP](../alcance-mvp.md); reglas en [turnos](../reglas/turnos.md) y [asignación](../reglas/asignacion.md).

---

## 5. Consequences

### Positive Consequences

- Cada regla que queda en el MVP se puede probar y defender.
- El cupo liberado por una cancelación a tiempo, más el aviso, cubre parte del caso de los lugares que quedan vacíos.

### Negative Consequences

- La hora de salida es una **estimación**. Hay que medir espera real vs. estimada para calibrarla.
- Sin lista de espera, un lugar que se libera después del cierre de la ventana queda vacío.
- Sin envejecimiento, en hora pico alguien que va a un piso bajo puede esperar detrás de los pisos altos. Si se retoma, la versión mínima es +1 nivel por ciclo sin atender, con tope, y un común nunca pasa a un prioritario.
- Quien reserva en los últimos 10 min no puede cancelar sin falta.

### Technical Implications

- Hace falta una **tabla de configuración** (tiempos, capacidad, conexiones) desde el inicio ([ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md)).
- `cancellation_wait_tolerance` y `priority_aging` quedan en NULL en `app_config` y el código no los usa.
- Las **dos inconsistencias** entre reglas (liberación de lugares prioritarios vs. cierre de la ventana, y umbral de no-show vs. ventana de check-in) se resolvieron en [#113](https://github.com/Benji-9/SmartElevate/issues/113): los lugares prioritarios se liberan 3 min antes y un escaneo entre +60 s y +2 min es *fuera de hora* y cuenta como no-show. Ver [decisiones tomadas](../reglas/turnos.md#decisiones-tomadas).

---

## 6. Implementation and Validation

- [x] Reserva, cancelación y cupo prioritario implementados (base de la revisión del 14/10).
- [ ] Valores y reglas nuevos del recorte (cancelación, cupo diario, vigencia docente) en [#218](https://github.com/Benji-9/SmartElevate/issues/218).
- [ ] Cancelación automática por ascensor fuera de servicio en [#134](https://github.com/Benji-9/SmartElevate/issues/134).
- [ ] Medir espera real vs. estimada para calibrar la hora de salida.
- [x] Documentación en [turnos](../reglas/turnos.md), [asignación](../reglas/asignacion.md) y [alcance del MVP](../alcance-mvp.md).

---

## 7. References

- [Reglas de turnos](../reglas/turnos.md), [asignación](../reglas/asignacion.md), [alcance del MVP](../alcance-mvp.md).
- [ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md).
- Issues: [#7](https://github.com/Benji-9/SmartElevate/issues/7), [#113](https://github.com/Benji-9/SmartElevate/issues/113), [#134](https://github.com/Benji-9/SmartElevate/issues/134), [#212](https://github.com/Benji-9/SmartElevate/issues/212), [#213](https://github.com/Benji-9/SmartElevate/issues/213), [#218](https://github.com/Benji-9/SmartElevate/issues/218).
