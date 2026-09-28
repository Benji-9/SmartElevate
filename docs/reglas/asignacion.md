# Reglas de asignación

> Estado: **propuesta** (complementa [#7](https://github.com/Benji-9/SmartElevate/issues/7)). Decisión de diseño en [ADR 0007](../adr/0007-modelo-de-turnos-y-reservas.md).

Cómo se decide a qué ascensor y salida va cada solicitud.

## Edificios

| Edificio | Rango de pisos |
| --- | --- |
| Lima | −2 a 10 |
| Independencia | −3 a 10 |

Los ascensores (IND1, IND2, L1, L2, L3), los pisos que sirve cada uno y las conexiones entre edificios se cargan en la **tabla de configuración**, no en el código. Relevamiento pendiente: [#22](https://github.com/Benji-9/SmartElevate/issues/22).

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

- La cercanía entre edificios se calcula con un **grafo**: nodos = edificio/ascensor, aristas = conexiones entre edificios, válidas por **rango de pisos** y **editables** en la configuración. Se recorre con **BFS**.
- **No se hardcodea** un orden fijo como IND2 → IND1 → L3 → L2 → L1.
- Solo se ofrece otro edificio si **hay conexión en el piso de origen**.
- El **tiempo de caminata** hasta el otro edificio se **suma a la espera estimada**.

## Ascensor accesible

El ascensor accesible está en **un solo edificio**. Para los prioritarios con movilidad reducida, las conexiones entre edificios son **críticas**: si no hay conexión accesible desde su piso de origen, la app no debe ofrecerles una alternativa que no pueden usar. Cuál es el edificio y qué conexiones son accesibles queda en el relevamiento de datos.
