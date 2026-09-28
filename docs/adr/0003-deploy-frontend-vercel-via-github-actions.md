# 0003. Deploy del frontend a Vercel vía GitHub Actions

- **Estado:** Aceptado
- **Fecha:** 2026-09-28

## Contexto

Queremos previews por PR y producción desde `main`, pero **solo si el CI pasó**. La integración nativa Git ↔ Vercel deploya cada push sin esperar a nuestro CI y no respeta la estrategia `main ← develop ← feature/*`.

## Opciones consideradas

1. **Integración Git de Vercel** — cero configuración, pero deploya aunque el CI falle.
2. **Job de deploy con `needs:` dentro de `ci-frontend.yml`** — simple, pero mezcla CI y CD y el deploy de un PR queda como un check más del PR.
3. **Workflow separado con `workflow_run`** sobre "CI Frontend" — CD desacoplado del CI, corre solo con conclusión `success`.

## Decisión

Opción 3, usando la Vercel CLI (`vercel pull` → `vercel build` → `vercel deploy --prebuilt`) y `"git": { "deploymentEnabled": false }` en `vercel.json` para que el workflow sea el único camino de deploy.

- PR → preview + comentario (se actualiza el mismo comentario en cada push).
- Push a `develop` → preview. Push a `main` → producción.
- Se deploya exactamente el `head_sha` que validó el CI.
- PRs desde forks se ignoran: `workflow_run` tiene acceso a secrets y no queremos ejecutar código ajeno con ellos.

### URL del backend

El backend se hostea fuera de Vercel. Vercel no interpola variables de entorno en `vercel.json`, así que el rewrite usa el placeholder literal `${BACKEND_URL}` y el workflow lo reemplaza (con `jq`) por la variable de GitHub `BACKEND_URL` antes de `vercel build`. Si no está definida, se quita el rewrite y se emite un warning.

## Consecuencias

- `workflow_run` solo se dispara con la versión del workflow que está en `main`: hasta el primer merge a `main` no hay deploys, y los cambios a `deploy-frontend.yml` no se pueden probar desde un PR.
- El deploy de un PR no aparece como check del PR; la URL llega por comentario y en el summary del run.
- El Root Directory del proyecto en Vercel debe quedar vacío (la CLI ya corre desde `frontend/`).
