# KPIs de congestión

> Estado: **aprobado** (parte de [#7](https://github.com/Benji-9/SmartElevate/issues/7)).

Todos salen de **eventos que la app ya registra** (reserva, cancelación, check-in, no-show), sin sensores extra.

**Baseline:** la encuesta del equipo muestra que el **43,5 %** espera entre 5 y 10 min. Los KPIs se comparan contra ese punto de partida.

| KPI | Definición | Fuente |
| --- | --- | --- |
| Espera real vs. estimada | Momento del check-in − hora estimada de salida, por reserva | Reserva + check-in |
| Ocupación por franja | Lugares usados / capacidad (10) por salida | Check-ins por salida |
| % de QR cumplidos | Check-ins *cumplida* / reservas no canceladas | Check-in |
| No-shows | Cantidad y tasa por día / usuario / franja | No-show |
| Distribución | Uso por ascensor, piso y hora del día | Reservas + check-ins |
| Espera de prioritarios | Espera real de los usuarios prioritarios, por separado | Reserva + check-in + categoría |

Para *otro ascensor* y *fuera de hora* ver [check-in](check-in-qr.md#resultados).

## Cálculo en el MVP

Lo implementa [#220](https://github.com/Benji-9/SmartElevate/issues/220) (`GET /api/admin/kpis`, solo ADMIN). Cuenta las reservas **no canceladas** (activas, usadas y no-shows) por hora de salida y deja afuera las que tienen un check-in del **simulador** de admin. Una salida está **resuelta** cuando pasó el umbral de no-show (+60 s): recién ahí se sabe si hubo check-in.

| KPI del panel | Cálculo | Sin datos (`null`) |
| --- | --- | --- |
| Turnos reservados | Reservas no canceladas del período | — |
| Check-ins realizados | Reservas usadas (check-in a tiempo, en su ascensor o en otro) / reservas no canceladas, de las salidas resueltas | Sin salidas resueltas |
| Ocupación promedio | Personas con check-in por salida resuelta que tuvo reservas; por núcleo, en % de la capacidad | Sin salidas resueltas |
| Espera promedio y % que espera 5–10 min | Necesitan la encuesta de espera, **fuera del MVP** | Siempre |

**Período anterior** ([#180](https://github.com/Benji-9/SmartElevate/issues/180)): `previous` trae los mismos totales para los días inmediatamente anteriores de igual largo y con los mismos filtros, en días de Buenos Aires: "hoy" → ayer, "últimos 7 días" → los 7 anteriores, "últimos 30 días" → los 30 anteriores. Es `null` si ese período no tuvo reservas.

## Congestión

`GET /api/congestion` (Inicio y el estado por núcleo del panel) da un nivel por núcleo a partir de la **ocupación**: en Inicio, lugares reservados / capacidad de las salidas de la ventana de reserva (los próximos 30 min); en el panel, la ocupación del período. **Media** desde el 60 % y **alta** desde el 85 % (`congestion_medium_from` y `congestion_high_from` en `app_config`). Las reglas no los fijaban: son una propuesta que cuadra con los ejemplos del diseño, **a calibrar en el piloto**.

## Filtros

El panel de administración filtra los KPIs por **período**, **sede** y **turno de cursada** ([#86](https://github.com/Benji-9/SmartElevate/issues/86)).

- **Turno mañana: 7:00 a 12:15.** Cubre las dos formas de cursada de la mañana: **7:45–11:45** y **8:15–12:15**, con margen para la llegada.
- Los turnos (nombre, inicio, fin) son **parámetros de la tabla de configuración**, en hora de Buenos Aires; el frontend los recibe del servidor (`GET /api/admin/shifts`). Por ahora solo está definido el turno mañana: sus horas son `morning_shift_start` y `morning_shift_end` (fin exclusivo); el nombre está en el código mientras haya uno solo.
