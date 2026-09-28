# Reglas de turnos

> Estado: **propuesta** para cerrar [#7](https://github.com/Benji-9/SmartElevate/issues/7). Decisión de diseño en [ADR 0007](../adr/0007-modelo-de-turnos-y-reservas.md). Los valores concretos viven en la [tabla de parámetros](README.md#parámetros-configurables): no hardcodearlos.

## 1. Franja y horario de operación

- **Una franja es una salida de ascensor**, de **~2 min**. No es un bloque de 15 minutos.
- Horario de operación: **7:00 a 22:30** (hora de Buenos Aires).
- Capacidad: **10 personas por salida**.

**Por qué ~2 min.** Con los tiempos medidos en las pruebas del equipo (14 s para que suban 10 personas, 3 s por piso), un viaje ida y vuelta al piso 10 ronda **1,5 min**: 30 s de subida, 30 s de bajada y el tiempo de carga y descarga. Con una franja de 15 min y cupo de 10, el ascensor haría varios viajes por franja y la app estaría reservando una fracción de su capacidad real. Con salidas de 2 min, un ascensor ofrece ~30 salidas por hora (~300 lugares/hora).

La hora de salida es **estimada**: el ascensor físico no sigue un horario. El KPI *espera real vs. estimada* mide qué tan buena es esa estimación.

## 2. Ventana de reserva

| Regla | Valor |
| --- | --- |
| Apertura | 30 min antes de la salida |
| Cierre | 2 min antes de la salida |
| Alcance | Solo salidas del mismo día |

La app es para movilidad inmediata; abrir con más anticipación facilita acaparar cupo.

## 3. Límites por usuario

- **1 reserva activa** a la vez.
- **Tope diario** de reservas (propuesta: 8).

## 4. Cancelación

- El usuario puede cancelar y **liberar el cupo hasta 1 min antes** de la salida.
- Una cancelación después de ese límite, o no presentarse, **cuenta como falta** ([no-show](#5-no-show)).
- **Excepción**: si la espera real supera la estimada por más de **X min** (a calibrar), cancelar **no cuenta como falta**. Cubre el caso del documento del proyecto: "cancelar si el tiempo no es el esperado".

## 5. No-show

- Una reserva **sin check-in QR a los 60 s de la salida** cuenta como no-show (ver [check-in](check-in-qr.md)).
- **3 no-shows en 7 días** → **24 h sin poder reservar**.
- **Prioritarios**: el no-show se registra (para KPIs) pero **no se suspende**.

Penalización leve y reversible: el objetivo es desalentar el acaparamiento, no castigar.

## 6. Cupo para prioritarios

- **Ascensor dedicado** para prioritarios, donde exista (ver [asignación](asignacion.md#ascensor-accesible)).
- En los ascensores comunes: **hasta 2 de los 10 lugares** de cada salida quedan reservados para prioritarios.
- Si nadie los usa, se liberan antes de la salida (ver [conflicto 1](#preguntas-abiertas)).

Así el cupo queda garantizado sin viajar con lugares vacíos. Se descartó una cola prioritaria pura, que es más difícil de explicar a los usuarios y de probar.

## 7. Lista de espera (propuesta)

Cuando una salida está llena, la solicitud entra en una **lista de espera** para las siguientes salidas, ordenada según las [reglas de prioridad](asignacion.md#orden-de-prioridad). Los lugares que se liberan después del cierre de la ventana (cancelaciones, prioritarios no usados) se asignan **automáticamente** al primero de la lista, en lugar de quedar vacíos.

## Preguntas abiertas

1. **Liberación de lugares prioritarios vs. cierre de ventana.** Las reglas dicen que los lugares prioritarios se liberan 1 min antes, pero la ventana de reserva cierra 2 min antes: un lugar liberado a −1 min nadie lo puede reservar. **Propuesta:** asignarlo a la lista de espera (sección 7), o liberarlo a −3 min para que entre en la ventana.
2. **Umbral de no-show vs. ventana de check-in.** El no-show se marca a los 60 s, pero el QR se acepta hasta 2 min después de la salida. **Propuesta** en [check-in](check-in-qr.md#resultados): entre +60 s y +2 min el escaneo se registra como *fuera de hora* y cuenta como no-show.
3. **Valores a calibrar**: tolerancia X de la cancelación, tope diario exacto.
