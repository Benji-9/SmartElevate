# Bloqueantes y decisiones abiertas

Resumen de lo que impide avanzar o requiere una decisión del equipo. **El seguimiento se hace en los [Issues de GitHub](https://github.com/Benji-9/SmartElevate/issues)** (responsable, discusión, avance); este archivo es el índice versionado. Cuando un issue se cierra, el ítem pasa a **Resueltos** con el link al PR o ADR.

**Estados:** 🔴 Bloqueante · 🟡 Abierto (no bloquea hoy) · 📝 Propuesta documentada, falta aprobación del equipo · 🟢 Resuelto

| ID | Issue | Estado | Tema | Impacto |
| --- | --- | --- | --- | --- |
| B-01 | [#5](https://github.com/Benji-9/SmartElevate/issues/5) | 🔴 | **Hosting del backend sin definir** (Render en evaluación) | Sin `BACKEND_URL`, el front en Vercel no llega a la API. También bloquea el storage de certificados (B-15). |
| B-05 | [#6](https://github.com/Benji-9/SmartElevate/issues/6) | 📝 | **Autenticación y prioridad** | Propuesta en [usuarios y prioridad](reglas/usuarios-y-prioridad.md) y [ADR 0009](adr/0009-autenticacion-y-prioridad.md). Bloquea `user` y `priority` hasta que se apruebe. |
| B-10 | [#7](https://github.com/Benji-9/SmartElevate/issues/7) | 📝 | **Reglas de negocio de turnos** | Propuesta en [reglas](reglas/README.md) y [ADR 0007](adr/0007-modelo-de-turnos-y-reservas.md). Las 2 inconsistencias se resolvieron en [#113](https://github.com/Benji-9/SmartElevate/issues/113). Quedan los valores X (tolerancia de cancelación) y N (envejecimiento) por definir ([preguntas abiertas](reglas/turnos.md#preguntas-abiertas)). |
| B-11 | [#22](https://github.com/Benji-9/SmartElevate/issues/22) | 🔴 | **Datos del edificio sin relevar** | Sin ascensores, pisos servidos y conexiones no se puede cargar la configuración ni asignar turnos. |
| B-12 | [#23](https://github.com/Benji-9/SmartElevate/issues/23) | 🟡 | **Dispositivo para el QR** | Sin QR no hay check-in, ni no-show, ni la mayoría de los KPIs. Para la demo alcanza con una página web. |
| B-13 | [#24](https://github.com/Benji-9/SmartElevate/issues/24) | 🟡 | **Datos de salud (Ley 25.326)** | Hay que validar consentimiento, retención y alcance antes de aceptar certificados reales. |
| B-14 | [#25](https://github.com/Benji-9/SmartElevate/issues/25) | 🟡 | **Proveedor de email** | Necesario para verificar cuentas @uade.edu.ar. |
| B-15 | [#26](https://github.com/Benji-9/SmartElevate/issues/26) | 🟡 | **Storage privado para certificados** | Depende de B-01. |
| B-07 | [#1](https://github.com/Benji-9/SmartElevate/issues/1) | 🟡 | **Deployment Protection en previews de Vercel** | Si sigue activa, quien no es miembro del equipo de Vercel no puede abrir las previews. Confirmar si se desactivó. |

## Cómo agregar un bloqueante

1. Abrir un issue con el template **Bloqueante / decisión** (queda con `type: decision` o `status: blocked`).
2. Sumar una fila acá con el próximo ID `B-NN` y el link al issue, en el mismo PR que lo detecta o en uno `docs: ...`.

## Resueltos

| ID | Issue | Tema | Resolución |
| --- | --- | --- | --- |
| B-06 | [#9](https://github.com/Benji-9/SmartElevate/issues/9) | Migraciones de base de datos | Flyway en todos los perfiles ([ADR 0004](adr/0004-migraciones-de-base-de-datos.md)) y primera migración `V1__init.sql` con la tabla de configuración `app_config` ([#108](https://github.com/Benji-9/SmartElevate/issues/108), [ADR 0010](adr/0010-integridad-de-reservas-tiempo-y-configuracion.md)). |
| B-16 | [#89](https://github.com/Benji-9/SmartElevate/issues/89) | Logo transparente y horizontal de SmartElevate | SVG vertical, horizontal y solo ícono en `frontend/src/assets/`, transparentes y con modo oscuro. |
| B-02 | [#1](https://github.com/Benji-9/SmartElevate/issues/1), [#2](https://github.com/Benji-9/SmartElevate/issues/2) | Proyecto de Vercel y secrets | Proyecto `smart-elevate` (team `takiprojects`) creado, integración Git desconectada y secrets cargados en GitHub. |
| B-03 | [#4](https://github.com/Benji-9/SmartElevate/issues/4) | El deploy solo corre desde `main` | Primer release (#12). Producción en https://smart-elevate.vercel.app. |
| B-04 | [#8](https://github.com/Benji-9/SmartElevate/issues/8) | API contract fuera del repo | Contrato OpenAPI generado desde el código y versionado en `docs/openapi.json`, con tipos TS generados para el front ([ADR 0006](adr/0006-contrato-api-openapi-code-first.md)). |
| B-09 | [#3](https://github.com/Benji-9/SmartElevate/issues/3) | Protección de ramas | `main` y `develop` protegidas: PR obligatorio con checks `backend-verify`, `frontend-verify` y `branch-name`; reviews por CODEOWNERS. |
| B-08 | [#10](https://github.com/Benji-9/SmartElevate/issues/10) | Imagen Docker del backend | Verificada: buildea, corre como usuario `spring` (no root), respeta `PORT` y responden `/api/ping` y `/actuator/health` contra Postgres. El CI del backend buildea la imagen en cada PR.
