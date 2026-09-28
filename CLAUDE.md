# CLAUDE.md

Contexto del repo para Claude Code. Leelo antes de tocar código.

## Proyecto

SmartElevate: MVP de 6 estudiantes de UADE para reducir la congestión de ascensores del campus. Turnos por franja horaria con **capacidad máxima de 10 personas por turno**, **usuarios prioritarios** (movilidad reducida, docentes) y **KPIs de congestión**. El README, los docs y la UI están en **español rioplatense**; el código (identificadores, commits) en inglés.

## Comandos

```bash
# Backend (desde backend/) — Java 17, Spring Boot 3.5
./mvnw verify                 # build + tests (lo que corre el CI)
./mvnw spring-boot:run        # perfil dev (H2), http://localhost:8080
./mvnw test -Dtest=PingControllerTest   # un test puntual

# Frontend (desde frontend/) — Node 24, Vite + React + TS
npm ci
npm run lint
npm test -- --run             # una pasada (sin --run queda en watch)
npm run build                 # tsc -b + vite build
npm run format                # prettier --write
```

Antes de dar una tarea por terminada, corré los comandos del lado que tocaste y confirmá que pasan.

## Arquitectura

### Backend (`backend/src/main/java/com/smartelevate/`)

- **Paquetes por feature**: `user`, `elevator`, `turn`, `priority`, cada uno con `controller/ service/ repository/ dto/ model/`. Lo transversal va en `common/` (`config`, `error`, `ping`).
- Controllers bajo `/api/...`, delgados: validan (`@Valid`), delegan en el service y devuelven DTOs (records). **Nunca exponer entidades JPA** en la API.
- La lógica de negocio (cupo de 10, prioridades) vive en los services y se testea con tests unitarios.
- Errores: lanzar excepciones y dejar que `common/error/GlobalExceptionHandler` arme el `ApiError`. Si hace falta un caso nuevo (p. ej. `TurnFullException` → 409), agregá el handler ahí; no armes respuestas de error a mano en los controllers.
- Configuración por variables de entorno (ver tabla en README). **Nunca hardcodear secretos** ni URLs de producción.
- Perfiles: `dev` (H2, default) y `prod` (Postgres). Los tests usan `dev`.
- Lombok está disponible; preferir `record` para DTOs y `@RequiredArgsConstructor` para inyección.

### Frontend (`frontend/src/`)

- `pages/` una pantalla por ruta · `components/` UI reutilizable · `features/<dominio>/` lógica y componentes de un dominio · `services/api.ts` único cliente HTTP · `hooks/` · `types/`.
- Toda llamada al backend pasa por `services/api.ts` (usa `VITE_API_URL`, default `/api`). No usar `fetch` directo en componentes.
- Rutas en `App.tsx` (React Router, importar de `react-router`). `App` no incluye el router: `main.tsx` usa `BrowserRouter`, los tests `MemoryRouter`.
- Tests con Vitest + Testing Library; mockear `fetch` con `vi.stubGlobal`. Buscar por rol/texto accesible, no por clases.

### CI/CD

- `ci-backend.yml` y `ci-frontend.yml`: PR y push a `main`/`develop`. **No agregar filtros `paths`** (rompe los required checks).
- `deploy-frontend.yml`: `workflow_run` sobre "CI Frontend". Si renombrás el workflow de CI, actualizá el nombre ahí.
- `frontend/vercel.json` contiene el placeholder literal `${BACKEND_URL}`: el workflow lo reemplaza. No lo cambies por una URL real.
- Validá workflows con `actionlint` antes de commitear.

## Convenciones de trabajo

- Ramas: `main` ← `develop` ← `feature/*`. Nunca commitear en `main`/`develop` directamente.
- Conventional Commits, chicos y atómicos: `feat(turn): ...`, `fix(frontend): ...`, `ci: ...`, `docs(adr): ...`.
- **No hacer push** ni abrir PRs sin que lo pida quien está trabajando.
- El trabajo se trackea en GitHub Issues con labels `type:*`, `area:*`, `priority:*` (ver README). Los PRs referencian su issue (`Closes #N`).
- Decisiones de arquitectura → nuevo ADR en `docs/adr/` (usar `template.md`). Algo que bloquea → `docs/bloqueantes.md`.
- Al fijar versiones de dependencias o actions, verificá la última versión real (Maven Central, npm, GitHub releases); no uses versiones de memoria.
- Spring Boot se queda en 3.x (ver ADR 0005): no aceptar upgrades a 4.x.

## Agentes disponibles (`.claude/agents/`)

- `backend-dev` — implementa features del backend siguiendo esta estructura.
- `frontend-dev` — implementa pantallas/features del frontend.
- `test-writer` — escribe o completa tests (JUnit/MockMvc y Vitest/RTL).
- `code-reviewer` — revisa un diff contra las convenciones del repo (solo lectura).
- `devops` — workflows de GitHub Actions, Vercel, Docker.
- `docs-keeper` — ADRs, `bloqueantes.md` y README.
