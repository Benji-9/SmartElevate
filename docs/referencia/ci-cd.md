# CI/CD

Workflows de GitHub Actions. Las reglas de ramas y los checks requeridos están en [CONTRIBUTING](../../CONTRIBUTING.md#ramas).

| Workflow | Cuándo | Qué hace |
| --- | --- | --- |
| [`ci-backend.yml`](../../.github/workflows/ci-backend.yml) | PR y push a `main`/`develop` | Job `backend-verify`: Temurin 25 + cache Maven, `./mvnw -B verify`, sube los reportes de tests como artifact. Job `backend-docker-build`: buildea la imagen de `backend/` |
| [`ci-frontend.yml`](../../.github/workflows/ci-frontend.yml) | PR y push a `main`/`develop` | Node 24 + cache npm, `npm ci`, chequeo de que `src/types/openapi.ts` coincide con `docs/openapi.json`, lint, tests, build |
| [`branch-policy.yml`](../../.github/workflows/branch-policy.yml) | PR a `main`/`develop` | Valida el nombre y el destino de la rama y agrega el label `type:` según el prefijo |
| [`deploy-frontend.yml`](../../.github/workflows/deploy-frontend.yml) | Cuando **CI Frontend** termina OK (`workflow_run`) | PR → preview + comentario con la URL · push a `develop` → preview · push a `main` → producción |
| [`keep-alive-backend.yml`](../../.github/workflows/keep-alive-backend.yml) | Cada 10 min, 07:00–22:59 (Buenos Aires) | `GET /api/ping` para que Render no duerma el backend ([detalle](../infraestructura.md#keep-alive)) |

Detalles:

- Los CI no usan filtros `paths`, así pueden configurarse como **required checks** sin quedar en "pending".
- Cada workflow tiene `concurrency` (cancela corridas viejas del mismo PR/rama; nunca un deploy de producción) y `permissions` mínimos.
- El deploy nunca corre para PRs de forks (el workflow tiene acceso a secrets).
- Los deploys automáticos de Vercel por Git están apagados (`"git": { "deploymentEnabled": false }` en `vercel.json`): **el único camino de deploy es el workflow**.
- Decisiones y trade-offs: [ADR 0003](../adr/0003-deploy-frontend-vercel-via-github-actions.md).
- El backend no tiene workflow de deploy: Render lo buildea solo desde `main` cuando pasa `backend-verify` ([ADR 0012](../adr/0012-hosting-del-backend-render-y-neon.md)).

## ¿Cómo llega `/api` al backend en Vercel?

`frontend/vercel.json` tiene un rewrite de `/api/(.*)` hacia `${BACKEND_URL}/api/$1`. Vercel **no** interpola variables en `vercel.json`, así que el workflow reemplaza el placeholder `${BACKEND_URL}` con la variable de GitHub `BACKEND_URL` antes de `vercel build`. Si la variable no está definida, el workflow emite un warning y quita ese rewrite (el front deploya igual, pero sin backend).
