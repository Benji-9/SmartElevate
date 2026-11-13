# ADR-0025: Zonas de pisos por ascensor para agrupar destinos antes de reservar

## Metadata

- **Status:** Accepted
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

La opción A responde a los drivers principales: el cupo atómico y `ReserveRequest` no cambian, lo que se muestra coincide con lo que se reserva, y la decisión (zonas, prioridades, ocupación) pasa a quien la conoce, el servidor. La B se descarta por tocar el contrato más delicado y por cupos aproximados en la lista. La C desperdicia capacidad y en L1 no ordena nada. La D no cumple el objetivo.

#### Decisiones del equipo (13/11/2026)

Se resolvieron las cinco preguntas que estaban abiertas:

1. **Las zonas cambian con la demanda.** No son fijas por franja horaria: el servidor agrupa según los destinos que ya tiene cada salida.
2. **Se adaptan a un ascensor no disponible.** Un ascensor inactivo no es candidato y los destinos de su zona pasan a los demás ascensores del núcleo por la misma regla de cercanía. Se apoya en [turnos §8](../reglas/turnos.md#8-ascensor-fuera-de-servicio), que ya no ofrece el ascensor inactivo ni le asigna reservas.
3. **N = 5 minutos** (valor recomendado, `zone_open_to_any_before` en `app_config`, a calibrar en el piloto). Razones: tiene que ser mayor que los 2 min en que cierra la ventana de reserva; ya pasó el plazo de cancelación sin falta (10 min), así que el cupo está estable; deja 3 min de ventana abierta antes del cierre; y coincide con el recordatorio de salida (`departure_reminder_before`, 5 min).
4. **Zonas iniciales a mano**, con la tabla de abajo (valores recomendados, a calibrar con la demanda del piloto). Son una semilla: la asignación real se ajusta con la demanda (decisión 1).
5. **Opción A.**

#### Cómo resuelve el servidor la oferta de cada hora

Entre las salidas de esa hora del núcleo elegido, se consideran candidatas las de ascensores **comunes y activos** que sirven origen y destino y tienen lugar para esa persona (con los 2 lugares reservados a prioritarios, `PrioritySeats`). Los dedicados (IND1 05 y 06) no se zonifican, y movilidad reducida sigue yendo a IND1 06. Después:

1. Si faltan **N minutos o menos** para la salida, **abierta**: se elige entre todas las candidatas por más lugares libres.
2. Si no, por **afinidad**: la salida cuyos destinos ya reservados estén más cerca del pedido (menor distancia en pisos).
3. Si ninguna tiene reservas, la del ascensor cuya **zona semilla** contiene el destino.
4. Si tampoco hay, la de más lugares libres.
5. Desempate: la más ocupada que todavía tenga lugar, para llenar una salida antes de abrir otra. Es una regla a validar en el piloto.

`ReservationService.reserve` sigue revalidando todo con el lock pesimista, así que si entre listar y confirmar la salida se llenó, responde `TURN_FULL` como hoy.

#### Zonas iniciales (semilla, a mano)

Destinos válidos: pisos −4 a 0 y 5 en adelante, porque la regla de pisos bajos bloquea los destinos 1 a 4 ([asignación](../reglas/asignacion.md#elegibilidad)). Los subsuelos se asignan al ascensor del rango más bajo del núcleo, porque casi nadie los pide. Los rangos se guardan en la configuración de ascensores, no en el código.

| Núcleo | Ascensores | Destinos de la zona |
| --- | --- | --- |
| IND2 | 33 | −4 a 0 y 5 a 8 |
| IND2 | 34 | 9 a 11 |
| L3 | 10, 11 | −3 a 0 y 5 a 6 |
| L3 | 12, 13 | 7 a 8 |
| L3 | 14, 15 | 9 a 10 |
| L2 (baja) | 07, 08 | −4 a 0 y 5 |
| L2 (alta) | 35 | 6 a 8 |
| L2 (alta) | 36 | 9 a 10 |
| L1 | 01, 02 | −3 a 0 y 5 a 6 |
| L1 | 03, 04 | 7 |

Criterio: un grupo de ascensores por tramo de pisos, con tramos más cortos arriba, donde cada piso suma 3 s al viaje. En L1 casi no hay qué ordenar (solo quedan 5, 6 y 7 tras la regla de pisos bajos), así que el efecto allí será chico. Estos valores son una recomendación sin datos de demanda: se calibran en el piloto.

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

La decisión está tomada; la implementación sigue pendiente y se rastrea en los sub-issues de [#298](https://github.com/Benji-9/SmartElevate/issues/298).

- [x] Resolver las preguntas abiertas y pasar el ADR a *Accepted*.
- [x] Actualizar [`docs/reglas/asignacion.md`](../reglas/asignacion.md) con la regla de zonas.
- [ ] Backend: modelo de zonas por ascensor (rango de pisos como semilla) y migración Flyway `V<n>__...sql` compatible con H2 y Postgres, con la tabla de zonas iniciales de arriba.
- [ ] Backend: parámetro `zone_open_to_any_before` (N = 5 min) en `app_config` (nunca hardcodeado) y en la tabla de parámetros de [turnos](../reglas/turnos.md).
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
