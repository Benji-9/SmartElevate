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
| **Ingreso manual del código de check-in** ([#238](https://github.com/Benji-9/SmartElevate/issues/238), [ADR 0021](adr/0021-check-in-sin-ingreso-manual.md)) | El token del QR tiene unos 80 caracteres opacos y rota cada ~30 s: no se puede tipear. Tampoco cubría la caída del kiosco, que sin red no tiene token para mostrar. Limitación aceptada: si el kiosco se queda sin red o se apaga, en ese ascensor no hay check-in. |
| **Encuesta de espera** | Medir la espera real necesita guardar la respuesta del usuario y decidir cómo se usa; sin eso la espera del panel queda en "Sin datos". |
| **Integración con OFALU para la marca de movilidad reducida** ([ADR 0024](adr/0024-movilidad-reducida-la-marca-viene-de-ofalu.md)) | UADE, a través de OFALU, recibe el aviso y marca a las personas con movilidad reducida, y maneja los certificados. SmartElevate no los sube ni los guarda. Falta definir con OFALU el formato, la vigencia y temporal vs permanente. Hasta entonces, ningún endpoint asigna la categoría. |
| **MFA** | Fuera del alcance de un MVP universitario. Con un segundo factor, la contraseña de 8 caracteres cumple NIST tal cual ([ADR 0015](adr/0015-politica-de-contrasenas-nist-800-63b.md)). |

Los parámetros de lo que queda afuera (`cancellation_wait_tolerance`, `priority_aging`) siguen en NULL en `app_config` y el código no los usa.

## Estado de implementación

Al 16/10/2026 (la marca de movilidad reducida quedó fuera de la app el mismo día, [ADR 0024](adr/0024-movilidad-reducida-la-marca-viene-de-ofalu.md)), con lo mergeado en `develop` hasta el [#250](https://github.com/Benji-9/SmartElevate/pull/250). **Hecho** = backend y pantalla contra la API real. **Mock** = solo existe la UI con datos simulados (`npm run dev`). **Pendiente** = sin código.

| Funcionalidad | Estado | Dónde |
| --- | --- | --- |
| Registro con email @uade.edu.ar, verificación por link | Hecho | [#185](https://github.com/Benji-9/SmartElevate/pull/185), [#189](https://github.com/Benji-9/SmartElevate/pull/189), [#197](https://github.com/Benji-9/SmartElevate/pull/197) |
| Login con JWT, límite de intentos, política de contraseñas NIST | Hecho | [#190](https://github.com/Benji-9/SmartElevate/pull/190), [#216](https://github.com/Benji-9/SmartElevate/pull/216), [#226](https://github.com/Benji-9/SmartElevate/pull/226) |
| Refresh token en cookie HttpOnly con defensa CSRF | Hecho | [#248](https://github.com/Benji-9/SmartElevate/pull/248), [#250](https://github.com/Benji-9/SmartElevate/pull/250), [ADR 0019](adr/0019-refresh-token-en-cookie-httponly.md), [0023](adr/0023-defensa-csrf-del-refresh-por-cookie.md) |
| Olvidé mi contraseña | Hecho | [#199](https://github.com/Benji-9/SmartElevate/pull/199) |
| Salidas, reserva con cupo atómico, reserva activa e historial | Hecho | [#187](https://github.com/Benji-9/SmartElevate/pull/187), [#201](https://github.com/Benji-9/SmartElevate/pull/201), [#243](https://github.com/Benji-9/SmartElevate/pull/243) |
| Cancelación (sin falta hasta 10 min antes, tardía con falta) | Hecho | [#209](https://github.com/Benji-9/SmartElevate/pull/209), [#240](https://github.com/Benji-9/SmartElevate/pull/240) |
| Cupo prioritario y ascensores dedicados | Hecho | [#210](https://github.com/Benji-9/SmartElevate/pull/210) |
| Roles, docentes validados por ADMIN, vigencia por cuatrimestre | Hecho (hay que cargar el calendario real, [B-19](bloqueantes.md)) | [#200](https://github.com/Benji-9/SmartElevate/pull/200), [#208](https://github.com/Benji-9/SmartElevate/pull/208), [#240](https://github.com/Benji-9/SmartElevate/pull/240), [#245](https://github.com/Benji-9/SmartElevate/pull/245) |
| Kiosco web `/qr/:ascensor` con credencial de dispositivo y QR rotativo | Hecho | [#234](https://github.com/Benji-9/SmartElevate/pull/234), [#236](https://github.com/Benji-9/SmartElevate/pull/236) |
| Simulador de kiosco para demos | Hecho (apagado por default) | [#236](https://github.com/Benji-9/SmartElevate/pull/236) |
| Check-in (`POST /api/check-ins`), sin ingreso manual | Hecho | [#235](https://github.com/Benji-9/SmartElevate/pull/235), [#239](https://github.com/Benji-9/SmartElevate/pull/239) |
| No-show, faltas y suspensión | Hecho | [#237](https://github.com/Benji-9/SmartElevate/pull/237) |
| Notificaciones por email (recordatorio, cancelaciones) y preferencias | Hecho | [#246](https://github.com/Benji-9/SmartElevate/pull/246), [#247](https://github.com/Benji-9/SmartElevate/pull/247) |
| Ascensor fuera de servicio (cancelación sin falta y aviso) | Hecho | [#249](https://github.com/Benji-9/SmartElevate/pull/249) |
| KPIs de congestión y panel de administración | Hecho. La espera promedio sale "Sin datos" (necesita la encuesta, fuera del MVP) | [#242](https://github.com/Benji-9/SmartElevate/pull/242) |
| Deploy del backend en Render con las variables de `prod` | Hecho | [#222](https://github.com/Benji-9/SmartElevate/issues/222) |

Fuera del MVP y sin código: envejecimiento, lista de espera (la UI de "Lugar liberado" se ocultó en [#178](https://github.com/Benji-9/SmartElevate/pull/178)), edificio alternativo, tolerancia X, ingreso manual del código y integración con OFALU, encuesta de espera (el panel la necesitaría para la espera real).
