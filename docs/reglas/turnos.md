# Reglas de turnos

> Estado: **aprobado** ([#7](https://github.com/Benji-9/SmartElevate/issues/7), con los cambios del 14/10 de [#218](https://github.com/Benji-9/SmartElevate/issues/218)). Decisión de diseño en los ADR [0007](../adr/0007-modelo-de-turnos-y-reservas.md) y [0018](../adr/0018-alcance-del-mvp-en-reglas-de-turnos.md); qué entra y qué no, en [alcance del MVP](../alcance-mvp.md). Los valores concretos viven en la [tabla de parámetros](README.md#parámetros-configurables): no hardcodearlos.

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
- **Tope diario**: 8 reservas por día. Una cancelación **sin falta devuelve** el lugar en el tope; una **tardía no** (decidido el 14/10; `V18`, [#218](https://github.com/Benji-9/SmartElevate/issues/218)).

## 4. Cancelación

- El usuario puede cancelar **sin falta hasta 10 min antes** de la salida, y el lugar se libera. El valor se calibra en el piloto (`cancellation_deadline_before`, [#218](https://github.com/Benji-9/SmartElevate/issues/218)). Cada reserva trae ese límite como `cancelFreeUntil` (salida − plazo, en UTC): a los 10 min justos todavía es sin falta.
- Entre ese límite y la salida, cancelar libera el lugar pero es una **cancelación tardía** (`CANCELLED_LATE`) y **cuenta como falta** ([no-show](#5-no-show)).
- **Después de la salida no se puede cancelar**: el turno pasa a no-show, o a usado si hubo check-in. Desde la hora de salida, cancelar responde `409` con `code` `DEPARTURE_ALREADY_PASSED` (también una reserva en no-show); una reserva con check-in, `ALREADY_CHECKED_IN`.
- **Compromiso aceptado:** la reserva abre 30 min y cierra 2 min antes, así que quien reserva en los últimos 10 min no puede cancelar sin falta. A cambio, un lugar cancelado a tiempo queda al menos 8 min en la ventana para que otro lo tome.
- **Fuera del MVP:** la excepción por demora (si la espera real supera la estimada por más de X min, cancelar no cuenta como falta). Necesita el check-in y medir la espera real ([alcance](../alcance-mvp.md)).

## 5. No-show

- Una reserva **sin check-in QR a los 60 s de la salida** cuenta como no-show (ver [check-in](check-in-qr.md)).
- **3 no-shows en 7 días** → **24 h sin poder reservar**. Después de cada suspensión el conteo **arranca de cero** (decidido el 14/10).
- Los **check-ins simulados** (simulador de admin) **no cuentan**: una reserva con check-in simulado no es falta ni asistencia, y queda fuera de los [KPIs](kpis.md) (decidido el 14/10).
- **Prioritarios**: el no-show se registra (para KPIs) pero **no se suspende**.
- Con check-in a tiempo la reserva pasa a **usada** (`USED`) y no es falta; un escaneo *fuera de hora* deja la reserva en no-show ([check-in](check-in-qr.md#resultados)).

Penalización leve y reversible: el objetivo es desalentar el acaparamiento, no castigar.

**Cómo se aplica** ([#231](https://github.com/Benji-9/SmartElevate/issues/231)):

- Un **job** pasa a `NO_SHOW` las reservas activas cuya salida pasó hace **más de** `no_show_after` (60 s): a los 60 s justos todavía no, porque el check-in hasta +60 s inclusive es a tiempo. Solo toca reservas activas, así que no pisa un usado ni una cancelada, y correrlo dos veces no cambia nada. Corre cada `app.no-show-job.interval` (15 s, parámetro técnico): una falta puede tardar hasta ese intervalo en registrarse.
- **Faltas**: no-shows y cancelaciones tardías ([sección 4](#4-cancelación)), fechadas a la **hora de salida** de la reserva. Una reserva con check-in `SIMULATED` no es falta, aunque el escaneo simulado haya sido *fuera de hora*. El criterio vive en un solo lugar, el mismo que usan los KPIs.
- **Suspensión**: cuando una falta completa `no_show_suspension_threshold` (3) dentro de `no_show_suspension_window` (7 días, contando hacia atrás desde esa falta y sin incluir el instante exacto de 7 días antes), el usuario no puede reservar por `no_show_suspension_duration` (24 h) **desde esa falta**. Reservar responde `409` con `code` `NO_SHOW_SUSPENDED`. Queda registrada en la tabla `no_show_suspension` (`V17`).
- **Cero después de la suspensión**: para la próxima solo cuentan las faltas **desde el fin** de la última suspensión. Las que caen durante la suspensión no cuentan para la siguiente: hacen falta 3 faltas nuevas.
- **Prioritarios**: la exención se evalúa **al intentar reservar**, con la prioridad vigente en ese momento (nunca el JWT). A un prioritario no se lo bloquea ni se le guarda la suspensión. Las faltas se registran igual: si la prioridad vence, cuentan las que haya desde la última suspensión.
- `GET /api/me/no-shows` devuelve las faltas de los últimos 7 días (si no está suspendido, solo las que cuentan para la próxima), los parámetros de la regla, el fin de la suspensión (si hay) y si el usuario está exento.

## 6. Cupo para prioritarios

- **Ascensor dedicado** para prioritarios, donde exista (ver [asignación](asignacion.md#ascensor-accesible)).
- En los ascensores comunes: **hasta 2 de los 10 lugares** de cada salida quedan reservados para prioritarios.
- Si nadie los usa, se liberan **3 min antes** de la salida, para que entren en la ventana de reserva (que cierra 2 min antes) y alguien más los pueda tomar.

Así el cupo queda garantizado sin viajar con lugares vacíos. Se descartó una cola prioritaria pura, que es más difícil de explicar a los usuarios y de probar.

## 7. Lista de espera (fuera del MVP)

**No entra en el MVP** (14/10, [alcance](../alcance-mvp.md)): el cupo que libera una cancelación a tiempo, más la notificación, cubre parte del caso. Queda la idea para la Fase 2:

Cuando una salida está llena, la solicitud entra en una **lista de espera** para las siguientes salidas, ordenada según las [reglas de prioridad](asignacion.md#orden-de-prioridad). Los lugares que se liberan después del cierre de la ventana (cancelaciones, prioritarios no usados) se asignan **automáticamente** al primero de la lista, en lugar de quedar vacíos.

## 8. Ascensor fuera de servicio

Si un ADMIN marca un ascensor fuera de servicio y tiene reservas activas ([#134](https://github.com/Benji-9/SmartElevate/issues/134)):

- Las reservas se **cancelan solas, sin falta**, y se devuelve el cupo diario.
- Se **avisa** a cada persona.
- Si otro ascensor tiene lugar, se le **ofrece** reubicarse. No se la mueve automáticamente.

## 9. Notificaciones por email

([#221](https://github.com/Benji-9/SmartElevate/issues/221)) Salen por el `EmailSender` (Brevo, ADR 0013). Las preferencias del usuario son `GET`/`PUT /api/me/notification-preferences`: `departureReminder` y `elevatorOutOfService`, **activas por defecto**: son avisos del servicio (qué pasó con tu turno), no marketing, y sin ellos la persona pierde información que necesita; se apagan cuando quiera. Los mails no llevan datos de salud ni de prioridad: solo ascensor, edificio y hora.

- **Recordatorio de salida:** un job manda **un solo mail por reserva activa** cuando la salida cae dentro de `departure_reminder_before` (5 min, a calibrar). La reserva se marca (`reminder_sent_at`) antes de mandar: si el mail falla no se reintenta. No lo reciben las canceladas, usadas ni no-show, ni quien reservó dentro de esa ventana (acaba de reservar). Las reservas con check-in simulado lo reciben como cualquier otra.
- **Ascensor fuera de servicio** (sección 8): el mail y su plantilla están listos (`TurnMailService.sendElevatorOutOfService`) pero **sin consumidor** hasta que se implemente #134, que tiene que llamarlo por cada reserva cancelada, **después del commit** de la cancelación (p. ej. `@TransactionalEventListener(phase = AFTER_COMMIT)`): dentro de la transacción un SMTP lento retiene la conexión y un rollback dejaría el mail ya enviado. Recibe un record plano (`OutOfServiceNotice`: usuario, hora, ascensor, núcleo y edificio), que el llamador arma antes del commit.
- **Lugar liberado:** **no se implementa.** Sin lista de espera (sección 7, fuera del MVP) no hay a quién avisarle: nadie se anota para una salida llena. Se retoma con la lista de espera.

## Decisiones tomadas

Resueltas en [#113](https://github.com/Benji-9/SmartElevate/issues/113):

1. **Liberación de lugares prioritarios vs. cierre de ventana.** Se liberan **3 min antes** de la salida (no 1 min), así entran en la ventana de reserva, que cierra 2 min antes. No hace falta la lista de espera para aprovecharlos.
2. **Umbral de no-show vs. ventana de check-in.** Un escaneo entre **+60 s y +2 min** se registra como *fuera de hora* y **cuenta como no-show** (ver [check-in](check-in-qr.md#resultados)).
3. **Tope diario:** 8 reservas por día.
4. **Pisos bajos:** no se puede reservar con destino en los pisos 1 a 4, salvo movilidad reducida (ver [asignación](asignacion.md#elegibilidad)).
5. **Rotación del QR:** cada 60 s. El 14/10 se bajó a ~30 s ([ADR 0016](../adr/0016-qr-firmado-con-hmac-y-kiosco-web.md)).

Resueltas el 14/10 ([#218](https://github.com/Benji-9/SmartElevate/issues/218), [ADR 0018](../adr/0018-alcance-del-mvp-en-reglas-de-turnos.md)):

6. **Plazo para cancelar sin falta:** 10 min antes de la salida, calibrable en el piloto ([sección 4](#4-cancelación)).
7. **Cupo diario:** cancelar sin falta lo devuelve; la cancelación tardía no.
8. **Cancelar después de la salida:** no se puede.
9. **Tolerancia X:** fuera del MVP.
10. **Envejecimiento N** ([#212](https://github.com/Benji-9/SmartElevate/issues/212)): fuera del MVP. Si se retoma: +1 nivel por ciclo sin atender, con tope, y un común nunca pasa a un prioritario.
11. **Lista de espera:** fuera del MVP ([sección 7](#7-lista-de-espera-fuera-del-mvp)).
12. **Ascensor fuera de servicio:** cancelación automática sin falta ([sección 8](#8-ascensor-fuera-de-servicio)).

## Preguntas abiertas

No quedan reglas de turnos sin decidir. X y N quedaron fuera del MVP (`cancellation_wait_tolerance` y `priority_aging` siguen en NULL y no se usan). Lo que falta:

1. **Calibrar en el piloto** el plazo de cancelación (10 min) y los tiempos de salida estimados.
2. **Accesibilidad de las conexiones** en silla de ruedas ([#22](https://github.com/Benji-9/SmartElevate/issues/22), [asignación](asignacion.md#ascensor-accesible)).
3. Lo que no es de turnos está en [bloqueantes](../bloqueantes.md).
