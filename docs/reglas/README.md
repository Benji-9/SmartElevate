# Reglas de negocio

Referencia funcional de SmartElevate: qué tiene que hacer el sistema. Todo está en estado **propuesta** hasta que el equipo lo apruebe en el PR y se cierren [#6](https://github.com/Benji-9/SmartElevate/issues/6) y [#7](https://github.com/Benji-9/SmartElevate/issues/7).

| Documento | Contenido | Módulo backend |
| --- | --- | --- |
| [turnos.md](turnos.md) | Franja, horario, ventana de reserva, límites, cancelación, no-show, cupo prioritario, lista de espera | `turn` |
| [asignacion.md](asignacion.md) | Elegibilidad, orden de prioridad, envejecimiento, edificio alternativo, ascensor accesible | `turn`, `elevator` |
| [check-in-qr.md](check-in-qr.md) | QR rotativo, ventana de lectura, resultados | `turn`, `elevator` |
| [usuarios-y-prioridad.md](usuarios-y-prioridad.md) | Registro, roles, certificado de prioridad, JWT, datos sensibles | `user`, `priority` |
| [kpis.md](kpis.md) | Indicadores de congestión y su fuente | estadísticas |

Decisiones de diseño: [ADR 0007](../adr/0007-modelo-de-turnos-y-reservas.md) (turnos), [0008](../adr/0008-check-in-con-qr-rotativo.md) (QR), [0009](../adr/0009-autenticacion-y-prioridad.md) (auth), [0010](../adr/0010-integridad-de-reservas-tiempo-y-configuracion.md) (integridad técnica). Términos: [glosario](../glosario.md).

## Parámetros configurables

Van en una **tabla de configuración validada** ([ADR 0010](../adr/0010-integridad-de-reservas-tiempo-y-configuracion.md)), **no hardcodeados**. Así QA puede probar variantes y el equipo puede calibrar sin redeployar.

| Parámetro | Valor propuesto | Notas |
| --- | --- | --- |
| Horario de operación | 07:00 – 22:30 | Hora de Buenos Aires |
| Duración de franja (salida) | 2 min | Ida y vuelta al piso 10 ≈ 1,5 min |
| Capacidad por salida | 10 | |
| Apertura de reserva | 30 min antes | Solo mismo día |
| Cierre de reserva | 2 min antes | |
| Reservas activas por usuario | 1 | |
| Reservas diarias por usuario | 8 | A calibrar |
| Límite de cancelación sin falta | 1 min antes | |
| Tolerancia de espera para cancelar sin falta | **X min** | **A definir** |
| Ventana de check-in | −1 min a +2 min | |
| Umbral de no-show | +60 s | Ver conflicto en [turnos](turnos.md#preguntas-abiertas) |
| Suspensión por no-show | 3 en 7 días → 24 h | No aplica a prioritarios |
| Lugares reservados para prioritarios | 2 de 10 | En ascensores comunes |
| Liberación de lugares prioritarios | 1 min antes | Ver conflicto en [turnos](turnos.md#preguntas-abiertas) |
| Envejecimiento de prioridad | **N min** | **A definir** |
| Rotación del QR | 30–60 s | |
| Access token / refresh token | 15 min / a definir | |
| Tamaño máximo de certificado | 5 MB | PDF, JPG, PNG |
| Tiempo de carga (10 personas) | 14 s | Medido por el equipo |
| Tiempo por piso | 3 s | Medido por el equipo |
| Zona horaria | America/Argentina/Buenos_Aires | Los instantes se guardan en UTC |
| Edificios | Lima −2 a 10 · Independencia −3 a 10 | |
| Turno mañana (filtro de KPIs) | 7:00–12:15 | Cursadas 7:45–11:45 y 8:15–12:15 ([kpis](kpis.md#filtros)) |
| Umbral de pisos bajos | **A confirmar** | [#22](https://github.com/Benji-9/SmartElevate/issues/22) |
| Ascensores, pisos servidos, conexiones | **A relevar** | [#22](https://github.com/Benji-9/SmartElevate/issues/22) |

## Pendientes fuera del código

| Issue | Tema |
| --- | --- |
| [#22](https://github.com/Benji-9/SmartElevate/issues/22) | Relevar ascensores, pisos servidos y conexiones entre edificios |
| [#23](https://github.com/Benji-9/SmartElevate/issues/23) | Dispositivo para mostrar el QR en cada ascensor |
| [#24](https://github.com/Benji-9/SmartElevate/issues/24) | Validar el tratamiento de datos de salud (Ley 25.326) |
| [#25](https://github.com/Benji-9/SmartElevate/issues/25) | Proveedor de email para verificar cuentas |
| [#26](https://github.com/Benji-9/SmartElevate/issues/26) | Storage privado para certificados |
