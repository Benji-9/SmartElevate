# SmartElevate

App para reducir la congestión de los ascensores del campus de UADE. Los alumnos reservan **turnos por franja horaria** con capacidad limitada (10 personas por turno), hay **usuarios prioritarios** (movilidad reducida, docentes) y una capa de **estadísticas / KPIs de congestión**.

> Estado: **esqueleto del MVP**. Compila, testea y deploya de punta a punta, pero todavía no tiene lógica de negocio.

## Stack

| Capa | Tecnología |
| --- | --- |
| Backend | Java 17, Spring Boot 3.5, Maven (wrapper), Spring Data JPA, H2 (dev) / PostgreSQL (prod), springdoc-openapi |
| Frontend | React 19, Vite 8, TypeScript, React Router, Vitest + React Testing Library, ESLint + Prettier |
| CI/CD | GitHub Actions |
| Deploy | Frontend en Vercel (vía GitHub Actions + Vercel CLI). Backend: hosting a definir ([bloqueante B-01](docs/bloqueantes.md)) |

## Estructura

```
.
├── backend/                  API REST (Spring Boot)
│   ├── src/main/java/com/smartelevate/
│   │   ├── common/           config (CORS, OpenAPI), manejo de errores, /api/ping
│   │   ├── user/             ┐
│   │   ├── elevator/         │ un paquete por módulo del API contract,
│   │   ├── turn/             │ con capas: controller / service / repository / dto / model
│   │   └── priority/         ┘
│   ├── Dockerfile
│   └── mvnw
├── frontend/                 SPA (Vite + React + TS)
│   ├── src/
│   │   ├── pages/            una pantalla por ruta
│   │   ├── components/       componentes reutilizables
│   │   ├── features/         lógica por dominio (turnos, ascensores, …)
│   │   ├── services/         cliente HTTP (api.ts)
│   │   ├── hooks/
│   │   └── types/
│   └── vercel.json
├── docs/                     bloqueantes, ADRs
├── .github/                  workflows, PR template, dependabot
├── .claude/                  agentes de Claude Code para el equipo
├── CLAUDE.md                 contexto del repo para Claude Code
└── docker-compose.yml        Postgres local
```

## Requisitos

- **JDK 17+** (el proyecto compila con `--release 17`; con JDK 21 también funciona)
- **Node.js 24 LTS** (ver `frontend/.nvmrc`; con nvm: `nvm use`)
- **Docker** (opcional, solo para Postgres local o para buildear la imagen del backend)

No hace falta tener Maven instalado: se usa `./mvnw` (en Windows `mvnw.cmd`).

## Levantar en local

### Backend

```bash
cd backend
./mvnw spring-boot:run        # perfil dev por defecto: H2 en memoria
```

- API: http://localhost:8080/api/ping → `{"status":"ok"}`
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Consola H2 (solo dev): http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:smartelevate`, usuario `sa`, sin password)

#### Con Postgres (perfil `prod`)

```bash
docker compose up -d                       # desde la raíz del repo
cd backend
export SPRING_PROFILES_ACTIVE=prod
export DATABASE_URL=jdbc:postgresql://localhost:5432/smartelevate
export DATABASE_USER=smartelevate
export DATABASE_PASSWORD=smartelevate
export JPA_DDL_AUTO=update                 # mientras no haya migraciones (ver ADR 0004)
./mvnw spring-boot:run
```

#### Variables de entorno del backend

| Variable | Default | Descripción |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` (H2) o `prod` (Postgres) |
| `PORT` | `8080` | Puerto HTTP |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes permitidos, separados por coma |
| `DATABASE_URL` | — | Solo `prod`. Formato JDBC: `jdbc:postgresql://host:5432/db` |
| `DATABASE_USER` | — | Solo `prod` |
| `DATABASE_PASSWORD` | — | Solo `prod` |
| `JPA_DDL_AUTO` | `validate` | Solo `prod`. Estrategia de Hibernate para el schema |

#### Imagen Docker

```bash
cd backend
docker build -t smartelevate-api .
docker run --rm -p 8080:8080 -e DATABASE_URL=... -e DATABASE_USER=... -e DATABASE_PASSWORD=... smartelevate-api
```

La imagen corre con perfil `prod`, usuario no root y respeta la variable `PORT`.

### Frontend

```bash
cd frontend
cp .env.example .env.local    # opcional: el default ya es /api
npm install
npm run dev                   # http://localhost:5173
```

En dev, Vite hace proxy de `/api` a `http://localhost:8080`, así que con el backend levantado la home muestra **"API conectada"**.

## Tests, lint y formato

| | Comando |
| --- | --- |
| Backend: build + tests | `cd backend && ./mvnw verify` |
| Frontend: tests (una pasada) | `cd frontend && npm test -- --run` |
| Frontend: tests en watch | `npm test` |
| Frontend: lint | `npm run lint` |
| Frontend: formatear | `npm run format` (chequear sin escribir: `npm run format:check`) |
| Frontend: build | `npm run build` |

Lo mismo que corre el CI del frontend: `npm ci && npm run lint && npm test -- --run && npm run build`.

## Estrategia de ramas

```
main       ← producción (deploy automático a Vercel prod)
  ↑ PR
develop    ← integración (deploy a Vercel preview)
  ↑ PR
feature/*  ← una rama por tarea (también fix/*, chore/*, docs/*)
```

- Nadie pushea directo a `main` ni a `develop`: todo entra por PR con CI en verde y al menos 1 review.
- Las ramas `feature/*` salen de `develop` y vuelven a `develop`.
- Para liberar, se abre un PR `develop → main`.

## Convención de commits

Usamos [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/):

```
<tipo>(<scope opcional>): <descripción en imperativo>
```

Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `style`, `perf`, `build`, `ci`, `chore`.
Scopes sugeridos: `backend`, `frontend`, `turn`, `elevator`, `user`, `priority`, `vercel`.

Ejemplos:

```
feat(turn): permitir reservar un turno en una franja con cupo
fix(frontend): mostrar error cuando la franja está llena
ci: cachear dependencias de Maven
```

Commits chicos y atómicos. El título del PR también sigue la convención.

## CI/CD

| Workflow | Cuándo | Qué hace |
| --- | --- | --- |
| [`ci-backend.yml`](.github/workflows/ci-backend.yml) | PR y push a `main`/`develop` | Temurin 17 + cache Maven, `./mvnw -B verify`, sube los reportes de tests como artifact |
| [`ci-frontend.yml`](.github/workflows/ci-frontend.yml) | PR y push a `main`/`develop` | Node 24 + cache npm, `npm ci`, lint, tests, build |
| [`deploy-frontend.yml`](.github/workflows/deploy-frontend.yml) | Cuando **CI Frontend** termina OK (`workflow_run`) | PR → preview + comentario con la URL · push a `develop` → preview · push a `main` → producción |

Detalles:

- Los CI no usan filtros `paths`, así pueden configurarse como **required checks** sin quedar en "pending".
- Cada workflow tiene `concurrency` (cancela corridas viejas del mismo PR/rama; nunca un deploy de producción) y `permissions` mínimos.
- El deploy nunca corre para PRs de forks (el workflow tiene acceso a secrets).
- Los deploys automáticos de Vercel por Git están apagados (`"git": { "deploymentEnabled": false }` en `vercel.json`): **el único camino de deploy es el workflow**.
- Decisiones y trade-offs: [ADR 0003](docs/adr/0003-deploy-frontend-vercel-via-github-actions.md).

### ¿Cómo llega `/api` al backend en Vercel?

`frontend/vercel.json` tiene un rewrite de `/api/(.*)` hacia `${BACKEND_URL}/api/$1`. Vercel **no** interpola variables en `vercel.json`, así que el workflow reemplaza el placeholder `${BACKEND_URL}` con la variable de GitHub `BACKEND_URL` antes de `vercel build`. Si la variable no está definida, el workflow emite un warning y quita ese rewrite (el front deploya igual, pero sin backend).

## Setup manual pendiente (una sola vez)

Cada bloque tiene su issue con checklist: Vercel [#1](https://github.com/Benji-9/SmartElevate/issues/1) · secrets [#2](https://github.com/Benji-9/SmartElevate/issues/2) · protección de ramas [#3](https://github.com/Benji-9/SmartElevate/issues/3) · primer release [#4](https://github.com/Benji-9/SmartElevate/issues/4).

### 1. Vercel: crear el proyecto y linkearlo

1. Crear una cuenta/equipo en [vercel.com](https://vercel.com) (el plan Hobby alcanza para el MVP).
2. Instalar la CLI y loguearse:
   ```bash
   npm install --global vercel
   vercel login
   ```
3. Desde `frontend/`, linkear (o crear) el proyecto:
   ```bash
   cd frontend
   vercel link
   ```
   Elegir el scope (equipo) y "Link to existing project? No" → nombre `smartelevate`. Se crea `frontend/.vercel/project.json` (está en `.gitignore`, **no se commitea**) con:
   ```json
   { "orgId": "team_xxx", "projectId": "prj_xxx" }
   ```
   Esos son `VERCEL_ORG_ID` y `VERCEL_PROJECT_ID`.
4. En el dashboard del proyecto → **Settings → General → Root Directory**: dejarlo **vacío**. El workflow ya corre la CLI desde `frontend/`; si se pone `frontend` ahí, la CLI busca `frontend/frontend` y falla.
5. **Settings → Git**: no conectar el repo (o, si se conectó, igual queda desactivado por `vercel.json`).

### 2. Vercel: crear el token

**Account Settings → Tokens → Create Token**, con scope al equipo del proyecto. Copiarlo: es `VERCEL_TOKEN`.

### 3. GitHub: cargar secrets y variables

En el repo → **Settings → Secrets and variables → Actions**:

- **Secrets** (pestaña *Secrets*):
  - `VERCEL_TOKEN`
  - `VERCEL_ORG_ID`
  - `VERCEL_PROJECT_ID`
- **Variables** (pestaña *Variables*):
  - `BACKEND_URL` — URL pública del backend, sin `/` final (p. ej. `https://smartelevate-api.onrender.com`). Se puede definir distinta por environment (`preview` / `production`) en **Settings → Environments**; los environments los crea el workflow en la primera corrida.

### 4. Vercel: variables de entorno

En el proyecto → **Settings → Environment Variables** (para *Production* y *Preview*):

- `VITE_API_URL` = `/api` (usa el rewrite de `vercel.json`). Alternativa: la URL absoluta del backend, pero ahí hay que agregar el dominio de Vercel a `CORS_ALLOWED_ORIGINS` del backend.

`vercel pull` las baja en cada deploy y Vite las embebe en el build.

### 5. GitHub: protección de ramas

**Settings → Branches → Add rule** (o *Rulesets*) para `main` y `develop`:

- Require a pull request before merging (1 aprobación)
- Require status checks to pass: `backend-verify` y `frontend-verify`
- Require branches to be up to date before merging
- Block force pushes

### 6. Primer merge a `main`

GitHub solo dispara `workflow_run` con la versión del workflow que está en la rama por defecto. **El deploy empieza a funcionar recién cuando `deploy-frontend.yml` llega a `main`.**

## Issues y labels

Todo el trabajo se trackea en [Issues](https://github.com/Benji-9/SmartElevate/issues). Cada PR referencia su issue (`Closes #N`) para que se cierre solo al mergear.

Templates: **Feature / tarea**, **Bug** y **Bloqueante / decisión**. Labels:

| Grupo | Labels |
| --- | --- |
| Tipo | `type: feature`, `type: bug`, `type: chore`, `type: docs`, `type: ci-cd`, `type: decision` |
| Área | `area: backend`, `area: frontend`, `area: infra`, `area: docs` |
| Prioridad | `priority: high`, `priority: medium`, `priority: low` |
| Estado | `status: blocked`, `setup: manual` (paso fuera del código, en GitHub/Vercel) |

Cada issue lleva al menos un `type:` y un `area:`. Los bloqueantes se registran además en [docs/bloqueantes.md](docs/bloqueantes.md).

## Documentación

- [docs/bloqueantes.md](docs/bloqueantes.md) — bloqueantes y decisiones abiertas
- [docs/adr/](docs/adr/) — Architecture Decision Records
- Swagger UI del backend: `/swagger-ui.html`

## Equipo

Proyecto de 6 estudiantes de UADE.
