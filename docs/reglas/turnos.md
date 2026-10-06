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
- **Tope diario**: 8 reservas por día.

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
- Si nadie los usa, se liberan **3 min antes** de la salida, para que entren en la ventana de reserva (que cierra 2 min antes) y alguien más los pueda tomar.

Así el cupo queda garantizado sin viajar con lugares vacíos. Se descartó una cola prioritaria pura, que es más difícil de explicar a los usuarios y de probar.

## 7. Lista de espera (propuesta)

Cuando una salida está llena, la solicitud entra en una **lista de espera** para las siguientes salidas, ordenada según las [reglas de prioridad](asignacion.md#orden-de-prioridad). Los lugares que se liberan después del cierre de la ventana (cancelaciones, prioritarios no usados) se asignan **automáticamente** al primero de la lista, en lugar de quedar vacíos.

## Decisiones tomadas

Resueltas en [#113](https://github.com/Benji-9/SmartElevate/issues/113):

1. **Liberación de lugares prioritarios vs. cierre de ventana.** Se liberan **3 min antes** de la salida (no 1 min), así entran en la ventana de reserva, que cierra 2 min antes. No hace falta la lista de espera para aprovecharlos.
2. **Umbral de no-show vs. ventana de check-in.** Un escaneo entre **+60 s y +2 min** se registra como *fuera de hora* y **cuenta como no-show** (ver [check-in](check-in-qr.md#resultados)).
3. **Tope diario:** 8 reservas por día.
4. **Pisos bajos:** no se puede reservar con destino en los pisos 1 a 4, salvo movilidad reducida (ver [asignación](asignacion.md#elegibilidad)).
5. **Rotación del QR:** cada 60 s.

## Preguntas abiertas

1. **Valores a calibrar**: tolerancia **X** de la cancelación ([sección 4](#4-cancelación)) y envejecimiento **N** de la prioridad ([asignación](asignacion.md#orden-de-prioridad)). Quedan sin valor en la tabla de configuración hasta que se definan.
