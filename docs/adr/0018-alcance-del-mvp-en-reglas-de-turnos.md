# 0018. Alcance del MVP en las reglas de turnos

- **Estado:** Aceptado
- **Fecha:** 2026-10-14
- **Issues:** [#218](https://github.com/Benji-9/SmartElevate/issues/218), [#212](https://github.com/Benji-9/SmartElevate/issues/212), [#213](https://github.com/Benji-9/SmartElevate/issues/213), [#134](https://github.com/Benji-9/SmartElevate/issues/134). Reemplaza en parte el [ADR 0007](0007-modelo-de-turnos-y-reservas.md).
- **Autores:** @Benji-9

## Contexto

El [ADR 0007](0007-modelo-de-turnos-y-reservas.md) fijó la cancelación libre hasta 1 min antes, una lista de espera, envejecimiento de la prioridad y edificio alternativo por BFS. Con la reserva, la cancelación y el cupo prioritario ya implementados, el equipo revisó el 14/10 qué entra en el MVP. Varias de esas piezas dependen de datos que todavía no hay (check-in, espera real medida) y no llegan a probarse bien antes de la entrega.

## Opciones consideradas

1. **Mantener todo el ADR 0007**: el modelo completo, pero con piezas que no se pueden calibrar sin check-in ni mediciones, y menos tiempo para probar lo central.
2. **Recortar al núcleo** (reservar, cancelar, cupo prioritario, check-in) y pasar el resto a una Fase 2: menos funcionalidad, pero cada regla que queda se puede probar y defender.

## Decisión

Opción **2**. Cambia respecto del ADR 0007:

- **Cancelar sin falta hasta 10 min antes** de la salida (antes, 1 min), calibrable en el piloto. Compromiso aceptado: como la reserva abre 30 min y cierra 2 min antes, quien reserva en los últimos 10 min no puede cancelar sin falta.
- Cancelar sin falta **devuelve el cupo diario**; la cancelación tardía no. Después de la salida **no se puede cancelar**: el turno queda como no-show, o como usado si hubo check-in.
- **Fuera del MVP (Fase 2):** lista de espera, envejecimiento ([#212](https://github.com/Benji-9/SmartElevate/issues/212)), edificio alternativo ([#213](https://github.com/Benji-9/SmartElevate/issues/213); a lo sumo un aviso informativo) y la tolerancia X de la cancelación.
- **Ascensor fuera de servicio** con reservas activas ([#134](https://github.com/Benji-9/SmartElevate/issues/134)): se cancelan solas, sin falta, se devuelve el cupo y se avisa a cada persona. Si otro ascensor tiene lugar, se le ofrece reubicarse; no se la mueve sola.

Lo demás del ADR 0007 sigue igual: franja de ~2 min, ventana de 30 a 2 min, 1 reserva activa, tope diario, no-show y cupo prioritario. Detalle y el porqué de cada recorte en [alcance del MVP](../alcance-mvp.md).

## Consecuencias

- Los valores y reglas nuevos se implementan en [#218](https://github.com/Benji-9/SmartElevate/issues/218) (cancelación, cupo diario, vigencia docente) y [#134](https://github.com/Benji-9/SmartElevate/issues/134).
- Sin lista de espera, un lugar que se libera después del cierre de la ventana queda vacío. El cupo liberado por una cancelación a tiempo, más el aviso, cubre parte del caso.
- Sin envejecimiento, en hora pico alguien que va a un piso bajo puede esperar detrás de los pisos altos. Si se retoma, la versión mínima es +1 nivel por ciclo sin atender, con tope, y un común nunca pasa a un prioritario.
- `cancellation_wait_tolerance` y `priority_aging` quedan en NULL en `app_config` y el código no los usa.
