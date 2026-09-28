# Bloqueantes y decisiones abiertas

Registro vivo de lo que impide avanzar o que requiere una decisión del equipo. Cuando algo se resuelve, se marca como **Resuelto**, se anota cómo y (si corresponde) se linkea el ADR.

**Estados:** 🔴 Bloqueante · 🟡 Abierto (no bloquea hoy) · 🟢 Resuelto

| ID | Estado | Tema | Impacto | Próximo paso | Responsable |
| --- | --- | --- | --- | --- | --- |
| B-01 | 🔴 | **Hosting del backend sin definir** | Sin `BACKEND_URL`, el front en Vercel no llega a la API (el rewrite `/api` se omite). | Evaluar Render / Railway / Fly.io (free tier, Docker, Postgres gestionado). Documentar en un ADR. | _a asignar_ |
| B-02 | 🔴 | **Secrets de Vercel sin cargar** | `deploy-frontend.yml` falla en `vercel pull`. | Seguir el README → "Setup manual pendiente" pasos 1–4. | _a asignar_ |
| B-03 | 🔴 | **El deploy solo corre desde `main`** | `workflow_run` usa la versión del workflow de la rama por defecto: hasta el primer merge a `main` no hay deploys (ni previews). | Mergear `feature/repo-skeleton → develop → main` apenas estén los secrets. | _a asignar_ |
| B-04 | 🟡 | **API contract fuera del repo** | Los paquetes `user`, `elevator`, `turn`, `priority` siguen el contrato, pero el documento no está versionado junto al código. | Subirlo a `docs/api-contract.md` (u OpenAPI en `docs/openapi.yaml`) y mantenerlo en los PRs. | _a asignar_ |
| B-05 | 🟡 | **Autenticación / identidad** | No está definido cómo se identifica al alumno ni cómo se marca a un usuario prioritario (¿SSO UADE?, ¿email institucional?, ¿alta manual?). Bloquea `user` y `priority`. | Decidir alcance del MVP (propuesta: login con email institucional + JWT) y registrar ADR. | _a asignar_ |
| B-06 | 🟡 | **Migraciones de base de datos** | En `prod`, `ddl-auto=validate` va a fallar en cuanto haya entidades sin schema creado. | Ver [ADR 0004](adr/0004-migraciones-de-base-de-datos.md): sumar Flyway con la primera entidad. | _a asignar_ |
| B-07 | 🟡 | **Deployment Protection en previews de Vercel** | Por defecto Vercel pide login en las URLs de preview; quien no sea miembro del equipo de Vercel no las puede ver. | Decidir: invitar a los 6 al equipo de Vercel o desactivar la protección en previews (Settings → Deployment Protection). | _a asignar_ |
| B-08 | 🟡 | **Imagen Docker del backend no verificada localmente** | El `Dockerfile` se escribió pero no se pudo buildear en la máquina donde se armó el esqueleto (Docker daemon apagado). | Correr `docker build -t smartelevate-api backend/` y un `docker run` contra el Postgres de `docker-compose`. | _a asignar_ |
| B-09 | 🟡 | **Protección de ramas** | Sin reglas, se puede pushear directo a `main`/`develop` y mergear con CI en rojo. | Configurar según README → paso 5. | _a asignar_ |
| B-10 | 🟡 | **Reglas de negocio de turnos** | Duración de la franja, ventana de reserva, cancelación/no-show, cupo reservado para prioritarios dentro de los 10 lugares. | Definir con el equipo/docente y volcarlo en el API contract. | _a asignar_ |

## Resueltos

_(vacío)_
