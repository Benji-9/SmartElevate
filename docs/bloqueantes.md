# Bloqueantes y decisiones abiertas

Resumen de lo que impide avanzar o requiere una decisión del equipo. **El seguimiento se hace en los [Issues de GitHub](https://github.com/Benji-9/SmartElevate/issues)** (responsable, discusión, avance); este archivo es el índice versionado. Cuando un issue se cierra, el ítem pasa a **Resueltos** con el link al PR o ADR.

**Estados:** 🔴 Bloqueante · 🟡 Abierto (no bloquea hoy) · 📝 Propuesta documentada, falta aprobación del equipo · 🟢 Resuelto

| ID | Issue | Estado | Tema | Impacto |
| --- | --- | --- | --- | --- |
| B-11 | [#22](https://github.com/Benji-9/SmartElevate/issues/22) | 🟡 | **Accesibilidad de las conexiones entre núcleos** | Relevado ([#99](https://github.com/Benji-9/SmartElevate/pull/99), `V2`): todos los núcleos están conectados en cada piso, sin paredes divisorias, y cruzar de punta a punta (Independencia 2 a Lima 1) tarda ~45–70 s. **Falta confirmar** si las conexiones se pueden hacer en silla de ruedas ([asignación](reglas/asignacion.md#ascensor-accesible)). No bloquea el MVP: el edificio alternativo quedó afuera. Próximo paso: recorrerlas con ese criterio. |
| B-13 | [#24](https://github.com/Benji-9/SmartElevate/issues/24) | 🟡 | **Datos de salud (Ley 25.326)** | Postura del equipo: minimización (sin diagnóstico; certificado cifrado, acceso restringido, URLs firmadas cortas, borrado al resolver o vencer; [usuarios y prioridad](reglas/usuarios-y-prioridad.md#datos-sensibles)). Sigue a validar con la cátedra, porque el certificado mismo es dato de salud. No se aceptan certificados reales hasta entonces. |

## Cómo agregar un bloqueante

1. Abrir un issue con el template **Bloqueante / decisión** (queda con `type: decision` o `status: blocked`).
2. Sumar una fila acá con el próximo ID `B-NN` y el link al issue, en el mismo PR que lo detecta o en uno `docs: ...`.

## Resueltos

| ID | Issue | Tema | Resolución |
| --- | --- | --- | --- |
| B-17 | [#218](https://github.com/Benji-9/SmartElevate/issues/218) | Decisiones de reglas del 14/10 | Fuera del MVP: tolerancia X, envejecimiento N ([#212](https://github.com/Benji-9/SmartElevate/issues/212)) y lista de espera. Vigencia de la prioridad de docente: hasta el fin del cuatrimestre en curso, renovable. IND1 06 queda exclusivo para movilidad reducida. Cancelar sin falta hasta 10 min antes ([ADR 0018](adr/0018-alcance-del-mvp-en-reglas-de-turnos.md), [alcance del MVP](alcance-mvp.md), [turnos](reglas/turnos.md#decisiones-tomadas)). Los cambios de código van en #218. |
| B-18 | [#225](https://github.com/Benji-9/SmartElevate/issues/225) | Dónde viven los TTL de seguridad | Decidido el 14/10. Secretos en variables de entorno, técnicos y de seguridad en `application.yml` con override por variable, y reglas de negocio en `app_config` ([ADR 0017](adr/0017-donde-viven-los-parametros.md)). Los límites de intentos de login se movieron a `application.yml` en #225 (`V14`). |
| B-12 | [#23](https://github.com/Benji-9/SmartElevate/issues/23) | Dispositivo para el QR | Decidido el 14/10, sin hardware: kiosco web `/qr/:ascensor` en una tablet o pantalla del hall, con token firmado HMAC-SHA256 ([ADR 0016](adr/0016-qr-firmado-con-hmac-y-kiosco-web.md)). Se implementa en [#219](https://github.com/Benji-9/SmartElevate/issues/219). |
| B-07 | [#1](https://github.com/Benji-9/SmartElevate/issues/1) | Deployment Protection en previews de Vercel | Desactivada (14/10). Ojo: las previews quedan accesibles para **cualquiera con la URL**, no solo para el equipo. No subir datos reales a una preview. |
| B-05 | [#6](https://github.com/Benji-9/SmartElevate/issues/6) | Autenticación y prioridad | Aprobado: [ADR 0009](adr/0009-autenticacion-y-prioridad.md) y [usuarios y prioridad](reglas/usuarios-y-prioridad.md), con el tipo de usuario declarativo y el primer ADMIN por SQL ([#32](https://github.com/Benji-9/SmartElevate/issues/32)). |
| B-10 | [#7](https://github.com/Benji-9/SmartElevate/issues/7) | Reglas de negocio de turnos | Aprobado: [ADR 0007](adr/0007-modelo-de-turnos-y-reservas.md) y [reglas](reglas/README.md); decisiones de [#113](https://github.com/Benji-9/SmartElevate/issues/113) aplicadas en `V3`. X (tolerancia de cancelación) y N (envejecimiento) quedaron fuera del MVP el 14/10 (B-17). |
| B-14 | [#25](https://github.com/Benji-9/SmartElevate/issues/25) | Proveedor de email | Brevo por SMTP. Sin dominio propio el remitente sale como `@brevosend.com`, y la prueba a @uade.edu.ar llegó a la bandeja de entrada ([ADR 0013](adr/0013-email-con-brevo-y-storage-en-r2.md), [infraestructura](infraestructura.md#email-con-brevo)). |
| B-15 | [#26](https://github.com/Benji-9/SmartElevate/issues/26) | Storage privado para certificados | Bucket privado `smartelevate-certificados` en Cloudflare R2, con un token limitado al bucket ([ADR 0013](adr/0013-email-con-brevo-y-storage-en-r2.md), [infraestructura](infraestructura.md#storage-de-certificados-en-r2)). No se suben certificados reales hasta resolver B-13. |
| B-01 | [#5](https://github.com/Benji-9/SmartElevate/issues/5) | Hosting del backend | Backend en Render y Postgres en Neon, conectados al front con `BACKEND_URL` ([ADR 0012](adr/0012-hosting-del-backend-render-y-neon.md), [infraestructura](infraestructura.md)). |
| B-06 | [#9](https://github.com/Benji-9/SmartElevate/issues/9) | Migraciones de base de datos | Flyway en todos los perfiles ([ADR 0004](adr/0004-migraciones-de-base-de-datos.md)) y primera migración `V1__init.sql` con la tabla de configuración `app_config` ([#108](https://github.com/Benji-9/SmartElevate/issues/108), [ADR 0010](adr/0010-integridad-de-reservas-tiempo-y-configuracion.md)). |
| B-16 | [#89](https://github.com/Benji-9/SmartElevate/issues/89) | Logo transparente y horizontal de SmartElevate | SVG vertical, horizontal y solo ícono en `frontend/src/assets/`, transparentes y con modo oscuro. |
| B-02 | [#1](https://github.com/Benji-9/SmartElevate/issues/1), [#2](https://github.com/Benji-9/SmartElevate/issues/2) | Proyecto de Vercel y secrets | Proyecto `smart-elevate` (team `takiprojects`) creado, integración Git desconectada y secrets cargados en GitHub. |
| B-03 | [#4](https://github.com/Benji-9/SmartElevate/issues/4) | El deploy solo corre desde `main` | Primer release (#12). Producción en https://smart-elevate.vercel.app. |
| B-04 | [#8](https://github.com/Benji-9/SmartElevate/issues/8) | API contract fuera del repo | Contrato OpenAPI generado desde el código y versionado en `docs/openapi.json`, con tipos TS generados para el front ([ADR 0006](adr/0006-contrato-api-openapi-code-first.md)). |
| B-09 | [#3](https://github.com/Benji-9/SmartElevate/issues/3) | Protección de ramas | `main` y `develop` protegidas: PR obligatorio con checks `backend-verify`, `frontend-verify` y `branch-name`; reviews por CODEOWNERS. |
| B-08 | [#10](https://github.com/Benji-9/SmartElevate/issues/10) | Imagen Docker del backend | Verificada: buildea, corre como usuario `spring` (no root), respeta `PORT` y responden `/api/ping` y `/actuator/health` contra Postgres. El CI del backend buildea la imagen en cada PR.
