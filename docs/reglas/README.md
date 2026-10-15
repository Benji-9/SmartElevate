# Reglas de negocio

Referencia funcional de SmartElevate: qué tiene que hacer el sistema. El equipo **aprobó** estas reglas ([#6](https://github.com/Benji-9/SmartElevate/issues/6), [#7](https://github.com/Benji-9/SmartElevate/issues/7)). Se cambian por PR, y si el cambio da vuelta una decisión, con un ADR nuevo.

| Documento | Contenido | Módulo backend |
| --- | --- | --- |
| [turnos.md](turnos.md) | Franja, horario, ventana de reserva, límites, cancelación, no-show, cupo prioritario, ascensor fuera de servicio | `turn` |
| [asignacion.md](asignacion.md) | Elegibilidad, orden de prioridad, envejecimiento, edificio alternativo, ascensor accesible | `turn`, `elevator` |
| [check-in-qr.md](check-in-qr.md) | QR rotativo, ventana de lectura, resultados | `turn`, `elevator` |
| [usuarios-y-prioridad.md](usuarios-y-prioridad.md) | Registro, roles, certificado de prioridad, JWT, datos sensibles | `user`, `priority` |
| [kpis.md](kpis.md) | Indicadores de congestión y su fuente | estadísticas |

Decisiones de diseño: [ADR 0007](../adr/0007-modelo-de-turnos-y-reservas.md) y [0018](../adr/0018-alcance-del-mvp-en-reglas-de-turnos.md) (turnos), [0008](../adr/0008-check-in-con-qr-rotativo.md) y [0016](../adr/0016-qr-firmado-con-hmac-y-kiosco-web.md) (QR), [0009](../adr/0009-autenticacion-y-prioridad.md) (auth), [0010](../adr/0010-integridad-de-reservas-tiempo-y-configuracion.md) (integridad técnica), [0017](../adr/0017-donde-viven-los-parametros.md) (dónde viven los parámetros). Términos: [glosario](../glosario.md). Qué entra en el MVP y qué queda para la Fase 2: [alcance del MVP](../alcance-mvp.md).

## Parámetros configurables

Los valores de `app_config` se leen y validan al arrancar: **un cambio en la tabla se aplica reiniciando la app**.

Dónde vive cada parámetro lo fija el [ADR 0017](../adr/0017-donde-viven-los-parametros.md): las **reglas de negocio** en la tabla `app_config` (validada al arrancar, [ADR 0010](../adr/0010-integridad-de-reservas-tiempo-y-configuracion.md)), **nunca hardcodeadas**, para que QA pruebe variantes y el equipo calibre sin redeployar; los **técnicos y de seguridad** en `application.yml` con override por variable de entorno; los **secretos**, solo en variables de entorno ([README](../../README.md#variables-de-entorno-del-backend)).

**Regla práctica:** si alguien necesita cambiarlo en caliente sin que un deploy lo deje registrado → `app_config`; si no → `application.yml`. Casos decididos ([#222](https://github.com/Benji-9/SmartElevate/issues/222), [#226](https://github.com/Benji-9/SmartElevate/issues/226)):

| Tipo | Dónde vive | Ejemplos |
| --- | --- | --- |
| Regla de negocio | `app_config` | Cupo por salida, ventanas de reserva y check-in, `boarding_time` (tiempo de carga), `time_per_floor` (tiempo por piso) |
| Técnico o de seguridad | `application.yml`, con override por variable de entorno | TTL de JWT, límites de login, `app.time.zone` (`APP_TIME_ZONE`), rotación del QR (`app.kiosk.qr-ttl`, movido de `app_config` en [#229](https://github.com/Benji-9/SmartElevate/issues/229)), job de recordatorios (`app.reminder-job.interval` 30 s y `app.reminder-job.enabled`, `APP_REMINDER_JOB_INTERVAL` y `APP_REMINDER_JOB_ENABLED`, [#221](https://github.com/Benji-9/SmartElevate/issues/221)) |
| Dato del despliegue | Variable de entorno, **obligatoria en `prod`** (sin default); el default `localhost` vive solo en `application-dev.yml` | `APP_FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` |
| Secreto | Solo variable de entorno, **obligatoria en `prod`** | `JWT_SECRET`, `APP_KIOSK_QR_SECRET` (clave HMAC del QR, [#229](https://github.com/Benji-9/SmartElevate/issues/229)) |
| Constante en el código | El código: cambia solo por PR | Parámetros de Argon2id ([ADR 0015](../adr/0015-politica-de-contrasenas-nist-800-63b.md)), `maxAge` de CORS (1 h) |

Las obligatorias de `prod` se validan al arrancar con una sola mecánica (`RequiredProdProperties`: `@Validated` + `@NotBlank`): si falta una o está en blanco, la app no levanta.

### Reglas de negocio (`app_config`)

| Parámetro | Valor | Notas |
| --- | --- | --- |
| Horario de operación | 07:00 – 22:30 | Hora de Buenos Aires |
| Duración de franja (salida) | 2 min | Ida y vuelta al piso 10 ≈ 1,5 min |
| Capacidad por salida | 10 | |
| Apertura de reserva | 30 min antes | Solo mismo día |
| Cierre de reserva | 2 min antes | |
| Reservas activas por usuario | 1 | |
| Reservas diarias por usuario | 8 | Cuentan las reservas hechas en el día salvo las canceladas sin falta (`CANCELLED`): cancelar a tiempo devuelve el lugar en el tope; la cancelación tardía no ([#218](https://github.com/Benji-9/SmartElevate/issues/218)) |
| Límite de cancelación sin falta | 10 min antes | Decidido el 14/10, calibrable en el piloto (`cancellation_deadline_before`, `V18`). La reserva lo expone como `cancelFreeUntil`. Desde la salida no se puede cancelar |
| Tolerancia de espera para cancelar sin falta (X) | — | **Fuera del MVP** ([alcance](../alcance-mvp.md)). `cancellation_wait_tolerance` queda en NULL y no se usa |
| Ventana de check-in | −1 min a +2 min | |
| Recordatorio de salida por email | 5 min antes | **Propuesto, a calibrar en el piloto** (`departure_reminder_before`, `V20`). Mayor a 0 y menor que la apertura de reserva. Ver [turnos §9](turnos.md#9-notificaciones-por-email) |
| Umbral de no-show | +60 s | Entre +60 s y +2 min el escaneo es *fuera de hora* y cuenta como no-show |
| Suspensión por no-show | 3 en 7 días → 24 h | No aplica a prioritarios |
| Lugares reservados para prioritarios | 2 de 10 | En ascensores comunes |
| Liberación de lugares prioritarios | 3 min antes | Antes del cierre de la ventana (2 min) |
| Envejecimiento de prioridad (N) | — | **Fuera del MVP** ([#212](https://github.com/Benji-9/SmartElevate/issues/212)). `priority_aging` queda en NULL y no se usa |
| Vigencia de la prioridad de docente | Hasta el fin del cuatrimestre en curso | Renovable. Cierres en `semester_end_dates` (`V18`): fechas `yyyy-MM-dd` separadas por coma, en orden; hoy `2026-12-31`, **provisorio**: hay que cargar el calendario real de UADE antes del 31/12/2026 ([bloqueantes](../bloqueantes.md)). `V18` también les puso ese vencimiento a las aprobaciones previas, que no vencían. Si al arrancar no queda ningún cierre desde hoy, la app lo avisa en el log (WARN). Vence al terminar ese día (hora de Buenos Aires). Sin un cierre cargado desde hoy, aprobar un docente falla. `V18` borró `priority_teacher_validity` |
| Tiempo de carga (10 personas) | 14 s | Medido por el equipo |
| Tiempo por piso | 3 s | Medido por el equipo |
| Umbral de pisos bajos | 4 | Sin reserva con destino en los pisos 1 a 4; movilidad reducida exenta |
| Edificios | Lima −4 a 10 · Independencia −4 a 11 | Relevado en [#22](https://github.com/Benji-9/SmartElevate/issues/22) |
| Ascensores y pisos servidos | Ver [asignación](asignacion.md#ascensores-y-pisos-servidos) | Relevado en [#22](https://github.com/Benji-9/SmartElevate/issues/22) |
| Conexiones entre núcleos | IND2 – IND1 – L3 – L2 – L1, en todos los pisos compartidos | Ver [asignación](asignacion.md#conexiones). Punta a punta ~45–70 s; accesibilidad **a confirmar** |
| Ascensores dedicados | IND1 05 docentes · IND1 06 movilidad reducida | Ver [asignación](asignacion.md#ascensores-dedicados) |

### Técnicos y de seguridad (`application.yml`)

| Parámetro | Valor | Dónde vive |
| --- | --- | --- |
| Zona horaria | America/Argentina/Buenos_Aires | `app.time.zone`, override por `APP_TIME_ZONE` (Render corre en UTC). Los instantes se guardan en UTC |
| Rotación del QR | 30 s, y se acepta el del ciclo anterior | `app.kiosk.qr-ttl`, override por `APP_KIOSK_QR_TTL` ([ADR 0016](../adr/0016-qr-firmado-con-hmac-y-kiosco-web.md), [0020](../adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)). Movido en [#229](https://github.com/Benji-9/SmartElevate/issues/229): era `qr_rotation` de `app_config` y `V15` borró la fila |
| Access token | 15 min | `JWT_ACCESS_TTL` (`app.security.jwt.access-ttl`) |
| Refresh token | 7 días | `JWT_REFRESH_TTL` (`app.security.jwt.refresh-ttl`) |
| Link de verificación de email | Vale 24 h · reenvío cada 1 min | `app.verification.*` |
| Link de reset de contraseña | Vale 30 min · un pedido por minuto | `app.password-reset.*` |
| Límite de logins fallidos | 10 por cuenta y 100 por IP en 15 min | Ventana deslizante; por cuenta, como mucho 100 ([ADR 0015](../adr/0015-politica-de-contrasenas-nist-800-63b.md)). `app.login-throttle.*`, override por `APP_LOGIN_THROTTLE_*`. Se movió de `app_config` en [#225](https://github.com/Benji-9/SmartElevate/issues/225) (`V14` borró las filas de `V13`) |
| Pedidos de token por kiosco | 10 por minuto | `app.kiosk.rate-limit-*`, override por `APP_KIOSK_RATE_LIMIT_*`. Se espera ~1 cada 30 s |
| Simulador de kioscos | Apagado | `APP_KIOSK_SIMULATOR_ENABLED` (`app.kiosk.simulator-enabled`): sin él no se crean kioscos simulados |
| Certificado | Hasta 5 MB · PDF, JPG o PNG | Tipo validado por contenido (magic bytes), no por extensión. Llega con [#123](https://github.com/Benji-9/SmartElevate/issues/123) |

### Secretos (solo variables de entorno)

`JWT_SECRET`, `DATABASE_PASSWORD`, `SPRING_MAIL_PASSWORD`, `APP_STORAGE_SECRET_KEY` y la clave HMAC del QR, `APP_KIOSK_QR_SECRET` ([ADR 0016](../adr/0016-qr-firmado-con-hmac-y-kiosco-web.md)). Las credenciales de los kioscos no son variables: las genera el alta y en la base queda solo su hash ([ADR 0020](../adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)).

## Pendientes fuera del código

| Issue | Tema | Estado |
| --- | --- | --- |
| [#22](https://github.com/Benji-9/SmartElevate/issues/22) | Ascensores, pisos servidos y conexiones | Relevado. Punta a punta ~45–70 s, sin paredes divisorias. Falta confirmar la accesibilidad en silla de ruedas |
| [#23](https://github.com/Benji-9/SmartElevate/issues/23) | Dispositivo para mostrar el QR | Decidido: kiosco web en una tablet o pantalla del hall ([ADR 0016](../adr/0016-qr-firmado-con-hmac-y-kiosco-web.md)). Falta conseguir las pantallas |
| [#24](https://github.com/Benji-9/SmartElevate/issues/24) | Tratamiento de datos de salud (Ley 25.326) | Postura de minimización ([usuarios y prioridad](usuarios-y-prioridad.md#datos-sensibles)); falta validarla con la cátedra |
| [#25](https://github.com/Benji-9/SmartElevate/issues/25) | Proveedor de email | Resuelto: Brevo ([ADR 0013](../adr/0013-email-con-brevo-y-storage-en-r2.md)) |
| [#26](https://github.com/Benji-9/SmartElevate/issues/26) | Storage privado para certificados | Resuelto: Cloudflare R2 ([ADR 0013](../adr/0013-email-con-brevo-y-storage-en-r2.md)) |
