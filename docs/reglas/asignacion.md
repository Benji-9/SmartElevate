# Reglas de asignación

> Estado: **aprobado** (complementa [#7](https://github.com/Benji-9/SmartElevate/issues/7)). Decisión de diseño en [ADR 0007](../adr/0007-modelo-de-turnos-y-reservas.md).

Cómo se decide a qué ascensor y salida va cada solicitud.

> **Decidido, pendiente de implementar:** zonas de pisos por ascensor para agrupar destinos ([ADR 0025](../adr/0025-zonas-de-pisos-por-ascensor.md), 13/11/2026). Hasta que se implemente ([#298](https://github.com/Benji-9/SmartElevate/issues/298)), el front sigue ofreciendo la salida con más lugares libres. Ver [Zonas de pisos](#zonas-de-pisos).

## Edificios, núcleos y ascensores

Relevado en el campus ([#22](https://github.com/Benji-9/SmartElevate/issues/22)) a partir de los carteles de cada núcleo y del plano de UADE. Estos datos son el **seed** de la tabla de configuración; el código no los conoce.

| Edificio | Rango de pisos | Núcleos |
| --- | --- | --- |
| Lima | −4 a 10 | L1, L2, L3 |
| Independencia | −4 a 11 | IND1, IND2 |

Los núcleos de Chile (CH1–CH3), Salta (S2), UADE Labs y UADE Housing quedan fuera del alcance del MVP.

### Ascensores y pisos servidos

Un núcleo puede tener **varias baterías** con pisos distintos, así que cada ascensor guarda **su propia lista de pisos**, no un rango.

| Núcleo | Ascensores | Pisos servidos | Observaciones |
| --- | --- | --- | --- |
| IND2 | 33, 34 | −4 a 11, **sin el 1** | Único que llega al 11. No para en el 1 |
| IND1 | 05, 06 | −4 a 10 | **Dedicados** (ver [ascensores dedicados](#ascensores-dedicados)). No están numerados en el edificio: 05 y 06 son códigos nuestros |
| L3 | 10, 11, 12, 13, 14, 15 | −3 a 10 | |
| L2, batería baja | 07, 08 | −4 a 5 | Ascensores viejos |
| L2, batería alta | 35, 36 | −2, 0, 2 a 10 (sin el −1 ni el 1) | Ascensores nuevos |
| L1 | 01, 02, 03, 04 | −3 a 7 | |

Los carteles listan los pisos donde para cada batería; conviene validarlos contra la botonera de las cabinas.

### Conexiones

Según el plano, los núcleos forman una cadena: **IND2 – IND1 – L3 – L2 – L1**. Están conectados en **todos los pisos que comparten**, sin paredes divisorias, salvo el **piso 11 de IND2**, que queda aislado. Los rangos de abajo se toman de los pisos servidos de cada núcleo.

Relevado en [#22](https://github.com/Benji-9/SmartElevate/issues/22): cruzar de **punta a punta** (Independencia 2 a Lima 1) tarda **~45–70 s** caminando. No se midió cada tramo por separado; el relevamiento también confirmó las conexiones accesibles para movilidad reducida.

| Conexión | Pisos | Tiempo de caminata (tramo) | Accesible |
| --- | --- | --- | --- |
| IND2 ↔ IND1 | −4 a 0 y 2 a 10 (IND2 no para en el 1) | Sin medir (punta a punta ~45–70 s) | **A confirmar** |
| IND1 ↔ L3 | −3 a 10 | Sin medir (punta a punta ~45–70 s) | **A confirmar** |
| L3 ↔ L2 | −3 a 10 | Sin medir (punta a punta ~45–70 s) | **A confirmar** |
| L2 ↔ L1 | −3 a 7 | Sin medir (punta a punta ~45–70 s) | **A confirmar** |

## Elegibilidad

Una solicitud es válida si:

1. El piso de **origen es distinto del destino**.
2. Ambos pisos están **dentro del rango del edificio**.
3. El ascensor asignado **sirve ambos pisos**.
4. No aplica la **regla de pisos bajos**: los trayectos cortos se hacen por escalera, así que **no se puede reservar con destino en los pisos 1 a 4** (inclusive; el 4 es el umbral de la tabla de parámetros). Queda **exenta** la **movilidad reducida**, que no puede usar escaleras.

## Zonas de pisos

Agrupan los destinos cercanos en una misma salida para que haya menos paradas por viaje. Decisión y alternativas en el [ADR 0025](../adr/0025-zonas-de-pisos-por-ascensor.md). El usuario elige edificio, núcleo, origen, destino y hora; **el servidor asigna la salida** y se la muestra recién después de reservar.

- **Solo en ascensores comunes y activos.** Los dedicados (IND1 05 y 06) y movilidad reducida no se zonifican. Los 2 lugares reservados a prioritarios por salida siguen igual.
- **Las zonas cambian con la demanda.** Entre las salidas candidatas de una hora, el servidor prefiere la que ya lleva destinos más cercanos al pedido; si ninguna tiene reservas, la del ascensor cuya zona semilla contiene el destino; si tampoco, la de más lugares libres. Desempate: la más ocupada que todavía tenga lugar.
- **A `zone_open_to_any_before` minutos (5) de la salida** la preferencia se corta y se elige entre todas las candidatas por más lugares libres, para no salir vacíos.
- **Ascensor no disponible:** no es candidato (ver [turnos §8](turnos.md#8-ascensor-fuera-de-servicio)) y sus destinos pasan a los demás ascensores del núcleo por la misma regla de cercanía.
- La elegibilidad se sigue evaluando en el servidor al reservar; si la salida se llenó entre listar y confirmar, responde `TURN_FULL`.

Zonas semilla (destinos válidos: −4 a 0 y 5 en adelante; los subsuelos van con el ascensor del rango más bajo). Se guardan en la configuración de ascensores y se calibran en el piloto:

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

## Orden de prioridad

Cuando hay más solicitudes que lugares (lista de espera o lugares liberados), se ordenan por:

1. **Movilidad reducida**
2. **Docentes**
3. **Piso de destino más alto**
4. **Orden de llegada**

**Envejecimiento (aging): fuera del MVP** (14/10, [#212](https://github.com/Benji-9/SmartElevate/issues/212), [alcance](../alcance-mvp.md)). Solo tiene sentido con una cola de espera, que tampoco entra. Si se retoma en la Fase 2, la versión mínima es: **+1 nivel por ciclo sin atender, con tope**, y un común **nunca** pasa a un prioritario. Sin esto, en hora pico alguien que va al piso 5 podría esperar detrás de quienes van a pisos más altos.

La prioridad del usuario se evalúa **en el servidor al momento de reservar**, nunca desde un claim del token (ver [usuarios y prioridad](usuarios-y-prioridad.md#sesión-jwt)).

## Edificio alternativo

**Fuera del MVP** (14/10, [#213](https://github.com/Benji-9/SmartElevate/issues/213), [alcance](../alcance-mvp.md)): a lo sumo, un aviso informativo. Faltan los tiempos por tramo y la accesibilidad de las conexiones. Queda el diseño para la Fase 2.

Si el edificio de origen está congestionado, se puede ofrecer otro:

- La cercanía se calcula con un **grafo**: nodos = núcleos, aristas = conexiones entre núcleos, válidas por **rango de pisos** y **editables** en la configuración. Se recorre con **BFS**.
- **No se hardcodea** un orden fijo como IND2 → IND1 → L3 → L2 → L1.
- Solo se ofrece otro edificio si **hay conexión en el piso de origen**.
- El **tiempo de caminata** hasta el otro edificio se **suma a la espera estimada**.

## Ascensores dedicados

Los dos ascensores de IND1 no se usan como ascensores comunes:

| Ascensor | Uso | Estado |
| --- | --- | --- |
| IND1 05 | Exclusivo para **docentes** | Así funciona hoy |
| IND1 06 | Exclusivo para **movilidad reducida** (ascensor accesible) | Decidido el 14/10 |

- En un ascensor dedicado solo puede reservar quien tenga esa prioridad aprobada y vigente. Se evalúa en el servidor al reservar, como el resto de las prioridades.
- Los **lugares reservados para prioritarios** (2 de 10) siguen aplicando a los ascensores **comunes**.
- El uso de cada ascensor (común, docentes, movilidad reducida) es un dato de la **tabla de configuración**, no del código.

## Ascensor accesible

El ascensor accesible es **IND1 06**, con los pisos −4 a 10. En el plano, el acceso para sillas de ruedas está en IND1, sobre Av. Independencia (entrada del estacionamiento). Para los prioritarios con movilidad reducida, las conexiones entre núcleos son **críticas**: la app no debe ofrecer una alternativa que requiera una conexión no accesible. Las conexiones accesibles quedaron relevadas en [#22](https://github.com/Benji-9/SmartElevate/issues/22).
