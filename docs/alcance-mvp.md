# Alcance del MVP

Qué entra en el MVP de SmartElevate y qué queda para una Fase 2, con el porqué. Lo decidió el equipo el 14/10 ([#218](https://github.com/Benji-9/SmartElevate/issues/218), [ADR 0018](adr/0018-alcance-del-mvp-en-reglas-de-turnos.md)). Las reglas en detalle están en [docs/reglas/](reglas/README.md).

## Entra en el MVP

| Qué | Dónde se define |
| --- | --- |
| Cuenta con email @uade.edu.ar verificado, login con límite de intentos y "olvidé mi contraseña" | [usuarios y prioridad](reglas/usuarios-y-prioridad.md) |
| Reservar un lugar en una **salida de ascensor** (~2 min, cupo de 10, ventana de 30 a 2 min antes) | [turnos](reglas/turnos.md) |
| Cancelar sin falta hasta **10 min antes**; la cancelación a tiempo devuelve el cupo diario | [turnos §4](reglas/turnos.md#4-cancelación) |
| Cupo prioritario (2 de 10) y ascensores dedicados: IND1 05 docentes, IND1 06 movilidad reducida | [asignación](reglas/asignacion.md#ascensores-dedicados) |
| Prioridad de docente validada por un ADMIN, vigente hasta el fin del cuatrimestre | [usuarios y prioridad](reglas/usuarios-y-prioridad.md#roles-y-prioridad) |
| Certificado de movilidad reducida con datos mínimos ([#123](https://github.com/Benji-9/SmartElevate/issues/123)) | [usuarios y prioridad](reglas/usuarios-y-prioridad.md#certificado-de-prioridad) |
| Check-in con QR rotativo en un kiosco web, no-show y suspensión ([#219](https://github.com/Benji-9/SmartElevate/issues/219)) | [check-in](reglas/check-in-qr.md), [ADR 0016](adr/0016-qr-firmado-con-hmac-y-kiosco-web.md) |
| Ascensor fuera de servicio: cancelación automática sin falta y aviso ([#134](https://github.com/Benji-9/SmartElevate/issues/134)) | [turnos §8](reglas/turnos.md#8-ascensor-fuera-de-servicio) |
| KPIs de congestión en el panel de administración | [KPIs](reglas/kpis.md) |

## Fuera del MVP (Fase 2)

| Qué | Por qué queda afuera |
| --- | --- |
| **Envejecimiento** de la prioridad ([#212](https://github.com/Benji-9/SmartElevate/issues/212)) | Solo tiene sentido con una cola de espera, que tampoco entra. Si se retoma, la versión mínima es +1 nivel por ciclo sin atender, con tope, y un común nunca pasa a un prioritario. |
| **Lista de espera** | Suma estados, asignación automática y avisos en tiempo real. El cupo que libera una cancelación a tiempo, más la notificación, cubre parte del caso. |
| **Edificio alternativo** ([#213](https://github.com/Benji-9/SmartElevate/issues/213)) | Necesita los tiempos de caminata por tramo y saber qué conexiones son accesibles, y todavía no están medidos. A lo sumo, un aviso informativo. |
| **Tolerancia X** (cancelar sin falta si la espera real supera la estimada + X) | Hace falta el check-in y medir la espera real antes de elegir un valor. |
| **MFA** | Fuera del alcance de un MVP universitario. Con un segundo factor, la contraseña de 8 caracteres cumple NIST tal cual ([ADR 0015](adr/0015-politica-de-contrasenas-nist-800-63b.md)). |

Los parámetros de lo que queda afuera (`cancellation_wait_tolerance`, `priority_aging`) siguen en NULL en `app_config` y el código no los usa.
