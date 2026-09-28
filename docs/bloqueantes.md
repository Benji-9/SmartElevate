# Bloqueantes y decisiones abiertas

Resumen de lo que impide avanzar o requiere una decisión del equipo. **El seguimiento se hace en los [Issues de GitHub](https://github.com/Benji-9/SmartElevate/issues)** (responsable, discusión, avance); este archivo es el índice versionado. Cuando un issue se cierra, el ítem pasa a **Resueltos** con el link al PR o ADR.

**Estados:** 🔴 Bloqueante · 🟡 Abierto (no bloquea hoy) · 🟢 Resuelto

| ID | Issue | Estado | Tema | Impacto |
| --- | --- | --- | --- | --- |
| B-01 | [#5](https://github.com/Benji-9/SmartElevate/issues/5) | 🔴 | **Hosting del backend sin definir** | Sin `BACKEND_URL`, el front en Vercel no llega a la API (el rewrite `/api` se omite). |
| B-02 | [#1](https://github.com/Benji-9/SmartElevate/issues/1), [#2](https://github.com/Benji-9/SmartElevate/issues/2) | 🔴 | **Proyecto de Vercel y secrets sin configurar** | `deploy-frontend.yml` falla en `vercel pull`. |
| B-03 | [#4](https://github.com/Benji-9/SmartElevate/issues/4) | 🔴 | **El deploy solo corre desde `main`** | `workflow_run` usa la versión del workflow de la rama por defecto: hasta el primer merge a `main` no hay deploys (ni previews). |
| B-04 | [#8](https://github.com/Benji-9/SmartElevate/issues/8) | 🟡 | **API contract fuera del repo** | Los módulos siguen el contrato, pero el documento no está versionado junto al código. |
| B-05 | [#6](https://github.com/Benji-9/SmartElevate/issues/6) | 🟡 | **Autenticación / identidad** | No está definido cómo se identifica al alumno ni cómo se marca a un usuario prioritario. Bloquea `user` y `priority`. |
| B-06 | [#9](https://github.com/Benji-9/SmartElevate/issues/9) | 🟡 | **Migraciones de base de datos** | En `prod`, `ddl-auto=validate` falla en cuanto haya entidades sin schema. Ver [ADR 0004](adr/0004-migraciones-de-base-de-datos.md). |
| B-07 | [#1](https://github.com/Benji-9/SmartElevate/issues/1) | 🟡 | **Deployment Protection en previews de Vercel** | Por defecto Vercel pide login en las URLs de preview. |
| B-08 | [#10](https://github.com/Benji-9/SmartElevate/issues/10) | 🟡 | **Imagen Docker del backend no verificada** | El `Dockerfile` no se pudo buildear al armar el esqueleto (Docker daemon apagado). |
| B-09 | [#3](https://github.com/Benji-9/SmartElevate/issues/3) | 🟡 | **Protección de ramas** | Sin reglas, se puede pushear directo a `main`/`develop` y mergear con CI en rojo. |
| B-10 | [#7](https://github.com/Benji-9/SmartElevate/issues/7) | 🟡 | **Reglas de negocio de turnos** | Duración de franja, ventana de reserva, cancelación/no-show, cupo para prioritarios. |

## Cómo agregar un bloqueante

1. Abrir un issue con el template **Bloqueante / decisión** (queda con `type: decision` o `status: blocked`).
2. Sumar una fila acá con el próximo ID `B-NN` y el link al issue, en el mismo PR que lo detecta o en uno `docs: ...`.

## Resueltos

_(vacío)_
