# Reglas de asignación

> Estado: **propuesta** (complementa [#7](https://github.com/Benji-9/SmartElevate/issues/7)). Decisión de diseño en [ADR 0007](../adr/0007-modelo-de-turnos-y-reservas.md).

Cómo se decide a qué ascensor y salida va cada solicitud.

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

Según el plano, los núcleos forman una cadena: **IND2 – IND1 – L3 – L2 – L1**. Están conectados en **todos los pisos que comparten**, salvo el **piso 11 de IND2**, que queda aislado. Los rangos de abajo se toman de los pisos servidos de cada núcleo.

| Conexión | Pisos | Tiempo de caminata | Accesible |
| --- | --- | --- | --- |
| IND2 ↔ IND1 | −4 a 10 | **A medir** | **A confirmar** |
| IND1 ↔ L3 | −3 a 10 | **A medir** | **A confirmar** |
| L3 ↔ L2 | −3 a 10 | **A medir** | **A confirmar** |
| L2 ↔ L1 | −3 a 7 | **A medir** | **A confirmar** |

## Elegibilidad

Una solicitud es válida si:

1. El piso de **origen es distinto del destino**.
2. Ambos pisos están **dentro del rango del edificio**.
3. El ascensor asignado **sirve ambos pisos**.
4. No aplica la **regla de pisos bajos** (los trayectos cortos se hacen por escalera; el umbral sale del documento del proyecto y va en la tabla de parámetros), salvo para **movilidad reducida**, que queda **exenta** porque no puede usar escaleras.

## Orden de prioridad

Cuando hay más solicitudes que lugares (lista de espera o lugares liberados), se ordenan por:

1. **Movilidad reducida**
2. **Docentes**
3. **Piso de destino más alto**
4. **Orden de llegada**

**Envejecimiento (aging):** después de **N min** de espera (a calibrar), una solicitud sube de prioridad. Sin esto, en hora pico alguien que va al piso 5 podría esperar indefinidamente detrás de quienes van a pisos más altos.

La prioridad del usuario se evalúa **en el servidor al momento de reservar**, nunca desde un claim del token (ver [usuarios y prioridad](usuarios-y-prioridad.md#sesión-jwt)).

## Edificio alternativo

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
| IND1 06 | Exclusivo para **movilidad reducida** (ascensor accesible) | **Propuesta** del proyecto |

- En un ascensor dedicado solo puede reservar quien tenga esa prioridad aprobada y vigente. Se evalúa en el servidor al reservar, como el resto de las prioridades.
- Los **lugares reservados para prioritarios** (2 de 10) siguen aplicando a los ascensores **comunes**.
- El uso de cada ascensor (común, docentes, movilidad reducida) es un dato de la **tabla de configuración**, no del código.

## Ascensor accesible

El ascensor accesible es **IND1 06**, con los pisos −4 a 10. En el plano, el acceso para sillas de ruedas está en IND1, sobre Av. Independencia (entrada del estacionamiento). Para los prioritarios con movilidad reducida, las conexiones entre núcleos son **críticas**: si no hay conexión accesible desde su piso de origen hasta IND1, la app no debe ofrecerles una alternativa que no pueden usar. Qué conexiones son accesibles sigue **a confirmar** ([#22](https://github.com/Benji-9/SmartElevate/issues/22)).
