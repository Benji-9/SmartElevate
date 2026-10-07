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

## Filtros

El panel de administración filtra los KPIs por **período**, **sede** y **turno de cursada** ([#86](https://github.com/Benji-9/SmartElevate/issues/86)).

- **Turno mañana: 7:00 a 12:15.** Cubre las dos formas de cursada de la mañana: **7:45–11:45** y **8:15–12:15**, con margen para la llegada.
- Los turnos (nombre, inicio, fin) son **parámetros de la tabla de configuración**, en hora de Buenos Aires; el frontend los recibe del servidor. Por ahora solo está definido el turno mañana.
