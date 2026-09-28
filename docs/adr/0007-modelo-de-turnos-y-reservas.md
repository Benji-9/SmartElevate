# 0007. Modelo de turnos: la franja es una salida de ascensor

- **Estado:** Propuesto
- **Fecha:** 2026-09-28
- **Issue:** [#7](https://github.com/Benji-9/SmartElevate/issues/7)

## Contexto

Hay que definir qué es un "turno", cuánto dura y cómo se reparte el cupo de 10 personas entre usuarios comunes y prioritarios. Según las mediciones del equipo (14 s para que suban 10 personas, 3 s por piso), un viaje ida y vuelta al piso 10 tarda ~1,5 min. La encuesta muestra que el 43,5 % espera entre 5 y 10 min.

## Opciones consideradas

1. **Franjas de 15 min con cupo 10**: fáciles de entender, pero el ascensor hace ~10 viajes por franja y la app reservaría una fracción mínima de su capacidad real.
2. **Franja = una salida de ascensor (~2 min)**: refleja la capacidad real (~300 lugares/hora por ascensor) y la espera estimada tiene sentido.
3. **Cola sin franjas** (turno por orden de llegada): no hace falta modelar horarios, pero no permite planificar ni reservar.

Para prioritarios:

- **a. Cola prioritaria pura**: más difícil de explicar a los usuarios y de probar.
- **b. Ascensor dedicado + hasta 2 de 10 lugares reservados** en los comunes, liberados si nadie los usa: cupo garantizado sin asientos vacíos.

## Decisión

Opción **2** para turnos y **b** para prioritarios, con:

- Ventana de reserva de 30 a 2 min antes de la salida, solo del mismo día.
- 1 reserva activa por usuario y tope diario.
- Cancelación libre hasta 1 min antes.
- No-show con penalización leve: 3 en 7 días → 24 h sin reservar, sin suspender a prioritarios.
- **Lista de espera** ordenada por prioridad para reasignar lugares que se liberan después del cierre de la ventana.
- Asignación con orden movilidad reducida > docentes > piso más alto > llegada, **con envejecimiento**, y edificio alternativo calculado con **BFS sobre un grafo de conexiones configurable**.

Detalle: [turnos](../reglas/turnos.md), [asignación](../reglas/asignacion.md).

## Consecuencias

- La hora de salida es una **estimación**. Hay que medir espera real vs. estimada para calibrarla.
- Hace falta una **tabla de configuración** (tiempos, capacidad, conexiones) desde el inicio ([ADR 0010](0010-integridad-de-reservas-tiempo-y-configuracion.md)).
- Quedan **dos inconsistencias** entre reglas a resolver antes de implementar: la liberación de lugares prioritarios vs. el cierre de la ventana, y el umbral de no-show vs. la ventana de check-in. Ver [preguntas abiertas](../reglas/turnos.md#preguntas-abiertas).
