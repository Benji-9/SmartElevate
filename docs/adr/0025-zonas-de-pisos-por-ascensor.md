# ADR-0025: Zonas de pisos por ascensor para agrupar destinos antes de reservar

## Metadata

- **Status:** Proposed
- **Date:** 2026-11-13
- **Deciders:** Equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** [#298](https://github.com/Benji-9/SmartElevate/issues/298), [#22](https://github.com/Benji-9/SmartElevate/issues/22), [#101](https://github.com/Benji-9/SmartElevate/issues/101), [#7](https://github.com/Benji-9/SmartElevate/issues/7)
- **Related ADRs:** [ADR-0007](0007-modelo-de-turnos-y-reservas.md), [ADR-0009](0009-autenticacion-y-prioridad.md), [ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md), [ADR-0017](0017-donde-viven-los-parametros.md)

---

## 1. Context and Problem Statement

### Context

La idea del equipo: asignar pisos a cada ascensor (p. ej. "el 02 de Lima 1 sirve al piso 7") para ordenar a la gente antes de que saque turno. Con menos paradas por viaje, las salidas se acortan y se liberan franjas (14 s de carga + 3 s por piso, ver [turnos](../reglas/turnos.md)).

Hoy [`asignacion.md`](../reglas/asignacion.md) solo exige que el ascensor asignado **sirva origen y destino**. No hay criterio de agrupación por cercanía de destino.

Estado actual, verificado en el código:

- **Backend.** `ReserveRequest` recibe `departureId`, origen y destino. Cada `Departure` pertenece a un ascensor y `ReservationService.reserve` valida contra `departure.getElevator()`, con lock pesimista sobre la salida (`findByIdForUpdate`) y constraint único salida + lugar. `GET /api/departures` solo filtra por `elevator` y `building` (opcionales) y devuelve todas las salidas reservables de los ascensores que el usuario puede usar. El check-in compara el ascensor del QR con el reservado (`OTHER_ELEVATOR`).
- **Frontend.** El usuario elige núcleo, origen y destino, y después una franja por hora, no un ascensor. Pero el ascensor lo elige el front: `slotsOf` (`frontend/src/features/turn/catalog.ts`) agrupa por hora, descarta los ascensores que no paran en origen y destino (`elevatorsServing`) y ofrece **la salida con más lugares libres**. Después manda ese `departureId`. `SlotList` muestra "· Ascensor X" en cada franja antes de confirmar, como si fuera una opción.
- Repartir por lugares libres **llena parejo** todos los ascensores: lo opuesto a agrupar destinos.

### Problem

¿Cómo agrupamos destinos cercanos en un mismo ascensor sin romper el cupo atómico, las prioridades ni los ascensores dedicados, y quién decide la salida: el cliente (como hoy) o el servidor, que es quien conoce las zonas y la ocupación real?

Una asignación fija (un ascensor por piso) tiene problemas concretos:

1. **Capacidad desperdiciada.** Si el 02 solo atiende el 7 y van 3 personas, sale casi vacío mientras otro ascensor tiene la cola llena. Los pisos altos concentran la demanda.
2. **L1 tiene pocos destinos válidos.** Sirve de −3 a 7 y la regla de pisos bajos bloquea los destinos 1 a 4, así que arriba quedan 5, 6 y 7 con 4 ascensores: sería casi 1 a 1 y no ordenaría nada.
3. **Ascensor fuera de servicio.** Dejaría un piso sin cobertura (ya existe el aviso `elevatorOutOfService`). Hace falta una regla de reasignación.
4. **Cumplimiento voluntario.** La app no controla los ascensores: si alguien reserva el 02 y sube al 03, el esquema se desordena. Hay que mostrar con claridad cuál le tocó.

Decisión ya tomada (no se discute acá): el usuario elige edificio, núcleo, origen, destino y hora; **la app asigna el ascensor**.

---

## 2. Decision Drivers

- **Cupo atómico intacto** (lock pesimista y constraint único salida + lugar): el contrato de reserva es lo más delicado y no debería moverse sin necesidad.
- **Lo que ve el usuario es lo que obtiene:** el cupo mostrado tiene que ser el de la salida que realmente se le da.
- **Prioridad y zonas se evalúan en el servidor** ([ADR-0009](0009-autenticacion-y-prioridad.md)), nunca en el cliente.
- **Parámetros fuera del código** ([ADR-0017](0017-donde-viven-los-parametros.md)): zonas y minutos N en la tabla de configuración o `app_config`, no hardcodeados.
- **Reloj inyectable y UTC** ([ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md)): "faltan N minutos" se calcula con `Clock`/`CampusTime`.
- **La franja es una salida de ~2 min** ([ADR-0007](0007-modelo-de-turnos-y-reservas.md)); no cambia.
- **Respetar lo decidido:** zonas solo en ascensores comunes (IND1 05 y 06 son dedicados), 2 lugares reservados para prioritarios por salida, movilidad reducida va a IND1 06.
- **Capacidad real:** no dejar salidas vacías por zonas rígidas.
- **Medible en el piloto** con los KPIs de congestión ([kpis](../reglas/kpis.md)).
- Complejidad de implementación y de testing (la lógica pasa al backend con sus tests).

---

## 3. Considered Options

### Option A: Oferta resuelta por el servidor en `GET /departures`

Zonas por **rango de pisos** (no por piso), guardadas en la configuración de ascensores, aplicadas solo a los comunes. `GET /departures` recibe núcleo, origen y destino y devuelve **una oferta por hora ya resuelta**: la salida que ya lleva destinos cercanos y, si faltan N minutos y no se llenó, la que sirva el destino aunque sea de otra zona. El front sigue mandando `departureId`.

**Pros:**
- El cupo mostrado es el de la salida que realmente se le va a dar.
- `ReserveRequest` y la validación de `ReservationService` no cambian: el cupo atómico queda intacto.
- `slotsOf` y `elevatorsServing` desaparecen del front.
- Zonas y prioridades se evalúan en el servidor, que igual revalida al reservar.

**Cons:**
- Entre listar y confirmar, la mejor salida puede cambiar: se rechaza con el manejo actual (`TURN_FULL`, recargar y elegir otra).
- El endpoint depende de origen y destino, así que se pide una vez elegidos los pisos (hoy se pide por edificio y se reutiliza al cambiar de núcleo): más pedidos.
- Cambia el contrato de `GET /departures` (OpenAPI, front y tests).

### Option B: La reserva recibe núcleo + hora y el servidor elige la salida

`ReserveRequest` pasa a llevar núcleo y hora en lugar de `departureId`; el servidor elige la salida al reservar.

**Pros:**
- Más simple para el front: no resuelve ni conoce salidas.
- La decisión de zona se toma con la ocupación más fresca, al reservar.

**Cons:**
- El usuario confirma una hora sin saber qué cupo tendrá: el cupo de la lista es aproximado.
- Las reservas fallidas por cupo serían frecuentes en hora pico.
- Toca el contrato más delicado, el de la reserva, y hay que rearmar el lock pesimista (hoy se toma sobre una salida conocida de antemano).

### Option C: Zona fija, un ascensor por piso

Cada piso (o grupo fijo) lo atiende un solo ascensor del núcleo.

**Pros:**
- Regla simple de explicar y de verificar.
- Agrupa destinos al máximo.

**Cons:**
- Capacidad desperdiciada: salidas casi vacías con otro ascensor lleno.
- En L1 es casi 1 a 1 (solo quedan 5, 6 y 7 tras la regla de pisos bajos), así que no ordena nada.
- Un ascensor fuera de servicio deja un piso sin cobertura.
- Cumplimiento voluntario: la app no controla los ascensores.

### Option D: Sin zonas (estado actual)

El front sigue ofreciendo, en cada hora, la salida con más lugares libres.

**Pros:**
- No hay nada que construir ni migrar.
- Reparte parejo la ocupación entre ascensores.

**Cons:**
- No agrupa destinos: salidas con más paradas y más largas, que es justo lo que la idea quiere evitar.
- La decisión sigue en el cliente, que no conoce ocupación real ni zonas.
- `SlotList` sigue mostrando el ascensor como si fuera una opción.

---

## 4. Decision Outcome

**Chosen Option:** A, Oferta resuelta por el servidor en `GET /departures` (zonas por rango de pisos, como preferencia y no fijas, solo en ascensores comunes).

### Rationale

> **Esta es una propuesta pendiente de aprobación del equipo.** El ADR queda en *Proposed* hasta que el equipo la apruebe en el PR; ningún valor ni regla de abajo está decidido.

La opción A responde a los drivers principales: el cupo atómico y `ReserveRequest` no cambian, lo que se muestra coincide con lo que se reserva, y la decisión (zonas, prioridades, ocupación) pasa a quien la conoce, el servidor. La B se descarta por tocar el contrato más delicado y por cupos aproximados en la lista. La C desperdicia capacidad y en L1 no ordena nada. La D no cumple el objetivo.

Forma de la propuesta (a validar):

1. **Rangos de pisos, no pisos.** Ej.: el 01 atiende 5–6 y el 02 atiende 7. Viven en la tabla de configuración de ascensores, como el uso (común, docentes, movilidad reducida).
2. **Preferencia, no asignación fija.** El servidor busca, dentro del núcleo elegido, la salida que ya lleva destinos cercanos. Si faltan N minutos para la salida y no se llenó, se abre a cualquier destino que el ascensor sirva (N en `app_config`).
3. **Respetar lo vigente:** zonas solo en comunes, 2 lugares reservados para prioritarios por salida, movilidad reducida a IND1 06 sin zonificar, elegibilidad en el servidor ([ADR-0009](0009-autenticacion-y-prioridad.md)).
4. **Mostrar el ascensor asignado después de reservar** (turno y check-in) y **dejar de mostrarlo en la lista de franjas**.

#### Open questions

Pendientes de definir por el equipo; no hay respuesta ni valor propuesto todavía.

- [ ] ¿Las zonas son fijas por franja horaria o cambian según la demanda del día?
- [ ] ¿Qué pasa si el ascensor de la zona está fuera de servicio? (reasignar a otra zona, avisar, etc.) Hoy un ascensor inactivo no se ofrece y se cancelan sus reservas futuras ([turnos §8](../reglas/turnos.md#8-ascensor-fuera-de-servicio)).
- [ ] ¿Cuántos minutos antes de la salida (N) se abre a cualquier destino?
- [ ] ¿Cómo se definen las zonas iniciales de cada núcleo? (con datos de demanda del piloto o a mano)
- [ ] Opción A o B para el contrato de reserva (la recomendación es A, pero la confirma el equipo).

---

## 5. Consequences

### Positive Consequences

- Salidas con destinos más cercanos: menos paradas por viaje y, potencialmente, salidas más cortas.
- La decisión sale del cliente y se testea en el backend.
- El cupo atómico y el contrato de reserva no se tocan.
- El usuario deja de ver el ascensor como una opción que no tiene.

### Negative Consequences

- Entre listar y confirmar, la salida ofrecida puede llenarse o cambiar: se rechaza con `TURN_FULL` y hay que recargar.
- Más pedidos a `GET /departures` (depende de origen y destino).
- El esquema depende de que la gente use el ascensor asignado; si no, se desordena. Un aumento de `OTHER_ELEVATOR` en el check-in sería señal de eso.
- Parámetros nuevos (zonas, N) que calibrar en el piloto.

### Technical Implications

- **Backend:** `DepartureService.findBookable` pasa a recibir núcleo, origen y destino y a resolver la oferta por hora; reutiliza `PrioritySeats` y el filtrado por uso/prioridad existente. Reloj vía `Clock`/`CampusTime` para N.
- **Datos:** rangos de pisos de zona en la configuración de ascensores (migración Flyway) y N en `app_config`; sin hardcodear.
- **Contrato:** cambia `docs/openapi.json` (regenerar con `OpenApiSpecTest` y `npm run gen:api`).
- **Frontend:** `catalog.ts` (`slotsOf`, `elevatorsServing`) se simplifica o desaparece; `ReserveTurnPage` y `SlotList` pasan a pedir la oferta con origen y destino y dejan de mostrar el ascensor. `ActiveTurnCard` y `TurnDetails` ya muestran el ascensor asignado después de reservar.
- **Docs:** [`asignacion.md`](../reglas/asignacion.md) (regla de zonas e interacción con prioridades y dedicados), `docs/frontend/SCREENS.md` §04.
- Sin impacto en infraestructura.

---

## 6. Implementation and Validation

Nada de esto se hace hasta que el equipo apruebe el ADR y resuelva las *Open questions*.

- [ ] Resolver las *Open questions* y pasar el ADR a *Accepted* en el PR.
- [ ] Actualizar [`docs/reglas/asignacion.md`](../reglas/asignacion.md) con la regla de zonas (hoy solo tiene una nota de que está en discusión).
- [ ] Backend: modelo de zonas por ascensor (rango de pisos) y migración Flyway `V<n>__...sql` compatible con H2 y Postgres.
- [ ] Backend: parámetro N en `app_config` (nunca hardcodeado).
- [ ] Backend: `GET /api/departures` con núcleo, origen y destino que devuelve la oferta por hora resuelta; zonas solo en comunes; respeta lugares reservados para prioritarios y dedicados.
- [ ] Backend: tests unitarios de la resolución (zona, apertura tras N minutos con `Clock.fixed`, ascensor inactivo, dedicados, prioritarios) y de que `reserve` sigue revalidando.
- [ ] Contrato: regenerar `docs/openapi.json` y `frontend/src/types/openapi.ts`.
- [ ] Frontend: pedir la oferta con origen y destino; eliminar `slotsOf` y `elevatorsServing`; sacar el ascensor de `SlotList`; tests de Vitest.
- [ ] Docs de UI: actualizar `docs/frontend/SCREENS.md` §04 (Reservar turno).
- [ ] Piloto: medir ocupación por salida y proporción de `OTHER_ELEVATOR` antes y después con los KPIs de [kpis.md](../reglas/kpis.md), y simular con reservas de prueba.
- [ ] Piloto: calibrar zonas y N con los datos de demanda.

---

## 7. References

- Issue [#298](https://github.com/Benji-9/SmartElevate/issues/298), con relacionados [#22](https://github.com/Benji-9/SmartElevate/issues/22) (pisos servidos), [#101](https://github.com/Benji-9/SmartElevate/issues/101) (baterías y dedicados) y [#7](https://github.com/Benji-9/SmartElevate/issues/7) (modelo de turnos).
- [ADR-0007](0007-modelo-de-turnos-y-reservas.md), [ADR-0009](0009-autenticacion-y-prioridad.md), [ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md), [ADR-0017](0017-donde-viven-los-parametros.md).
- Reglas: [asignación](../reglas/asignacion.md), [turnos](../reglas/turnos.md), [KPIs](../reglas/kpis.md).
- Código: `ReservationService`, `DepartureService`, `DepartureController` (backend); `frontend/src/features/turn/catalog.ts`, `SlotList.tsx`.
