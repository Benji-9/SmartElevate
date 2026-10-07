# Reglas de negocio

Referencia funcional de SmartElevate: qué tiene que hacer el sistema. El equipo **aprobó** estas reglas ([#6](https://github.com/Benji-9/SmartElevate/issues/6), [#7](https://github.com/Benji-9/SmartElevate/issues/7)). Se cambian por PR, y si el cambio da vuelta una decisión, con un ADR nuevo.

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
| Reservas diarias por usuario | 8 | |
| Límite de cancelación sin falta | 1 min antes | |
| Tolerancia de espera para cancelar sin falta | **X min** | **A definir** |
| Ventana de check-in | −1 min a +2 min | |
| Umbral de no-show | +60 s | Entre +60 s y +2 min el escaneo es *fuera de hora* y cuenta como no-show |
| Suspensión por no-show | 3 en 7 días → 24 h | No aplica a prioritarios |
| Lugares reservados para prioritarios | 2 de 10 | En ascensores comunes |
| Liberación de lugares prioritarios | 3 min antes | Antes del cierre de la ventana (2 min) |
| Envejecimiento de prioridad | **N min** | **A definir** |
| Rotación del QR | 60 s | |
| Access token / refresh token | 15 min / a definir | |
| Tamaño máximo de certificado | 5 MB | PDF, JPG, PNG |
| Tiempo de carga (10 personas) | 14 s | Medido por el equipo |
| Tiempo por piso | 3 s | Medido por el equipo |
| Zona horaria | America/Argentina/Buenos_Aires | Los instantes se guardan en UTC |
| Edificios | Lima −4 a 10 · Independencia −4 a 11 | Relevado en [#22](https://github.com/Benji-9/SmartElevate/issues/22) |
| Turno mañana (filtro de KPIs) | 7:00–12:15 | Cursadas 7:45–11:45 y 8:15–12:15 ([kpis](kpis.md#filtros)) |
| Umbral de pisos bajos | 4 | Sin reserva con destino en los pisos 1 a 4; movilidad reducida exenta |
| Ascensores y pisos servidos | Ver [asignación](asignacion.md#ascensores-y-pisos-servidos) | Relevado en [#22](https://github.com/Benji-9/SmartElevate/issues/22) |
| Conexiones entre núcleos | IND2 – IND1 – L3 – L2 – L1, en todos los pisos compartidos | Ver [asignación](asignacion.md#conexiones). Tiempos de caminata **a medir** |
| Ascensores dedicados | IND1 05 docentes · IND1 06 movilidad reducida (propuesto) | Ver [asignación](asignacion.md#ascensores-dedicados). Conexiones accesibles **a confirmar** |

## Pendientes fuera del código

| Issue | Tema |
| --- | --- |
| [#22](https://github.com/Benji-9/SmartElevate/issues/22) | Relevar ascensores, pisos servidos y conexiones entre edificios |
| [#23](https://github.com/Benji-9/SmartElevate/issues/23) | Dispositivo para mostrar el QR en cada ascensor |
| [#24](https://github.com/Benji-9/SmartElevate/issues/24) | Validar el tratamiento de datos de salud (Ley 25.326) |
| [#25](https://github.com/Benji-9/SmartElevate/issues/25) | Proveedor de email para verificar cuentas |
| [#26](https://github.com/Benji-9/SmartElevate/issues/26) | Storage privado para certificados |
