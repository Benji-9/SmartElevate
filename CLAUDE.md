# CLAUDE.md

Contexto del repo para Claude Code. Leelo antes de tocar código.

## Proyecto

SmartElevate: MVP de 6 estudiantes de UADE para reducir la congestión de ascensores del campus. Turnos por franja horaria con **capacidad máxima de 10 personas por turno**, **usuarios prioritarios** (movilidad reducida, docentes) y **KPIs de congestión**. El README, los docs y la UI están en **español rioplatense**; el código (identificadores, commits) en inglés.

## Comandos

```bash
# Backend (desde backend/) — Java 25, Spring Boot 4.1
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

## Reglas de negocio

Antes de implementar algo de `turn`, `elevator`, `user` o `priority` (backend o UI), leé [`docs/reglas/`](docs/reglas/README.md) (y el [glosario](docs/glosario.md)). Puntos que no se negocian en el código:

- La franja es una **salida de ascensor (~2 min)**, no un bloque de 15 min (ADR 0007).
- **Parámetros en la tabla de configuración**, nunca hardcodeados (tiempos, ventanas, cupo, conexiones).
- **Cupo atómico** (lock pesimista o constraint único), instantes en **UTC**, franjas en **America/Argentina/Buenos_Aires**, y un **`Clock` inyectable**: nada de `Instant.now()` directo en services (ADR 0010).
  La hora se pide a `common/time/CampusTime` (`now()`, `today()`, `toInstant(fecha, hora)`) o al bean `Clock`; en tests, `Clock.fixed(...)`.
- La prioridad se evalúa **en el servidor al reservar**, nunca desde un claim del JWT. El rol y la prioridad no vienen del registro (ADR 0009).
- Los certificados son **datos de salud**: bucket privado, URLs firmadas cortas, borrado al resolver.

Si una regla es ambigua o contradictoria, frená y preguntá antes de elegir. Lo pendiente está en `docs/reglas/turnos.md#preguntas-abiertas` (hoy: los valores X y N, que siguen en NULL en `app_config`).

## Arquitectura

### Backend (`backend/src/main/java/com/smartelevate/`)

- **Paquetes por feature**: `user`, `elevator`, `turn`, `priority`, cada uno con `controller/ service/ repository/ dto/ model/`. Lo transversal va en `common/` (`config`, `error`, `ping`).
- Controllers bajo `/api/...`, delgados: validan (`@Valid`), delegan en el service y devuelven DTOs (records). **Nunca exponer entidades JPA** en la API.
- La lógica de negocio (cupo de 10, prioridades) vive en los services y se testea con tests unitarios.
- Errores: lanzar excepciones y dejar que `common/error/GlobalExceptionHandler` arme el `ApiError`. Si hace falta un caso nuevo (p. ej. `TurnFullException` → 409), agregá el handler ahí; no armes respuestas de error a mano en los controllers.
- Configuración por variables de entorno (ver tabla en README). **Nunca hardcodear secretos** ni URLs de producción.
- Perfiles: `dev` (H2, default) y `prod` (Postgres). Los tests usan `dev`.
- **Base de datos con Flyway** (ADR 0004): el schema lo crean las migraciones de `src/main/resources/db/migration/V<n>__<descripcion>.sql`, en todos los perfiles, y Hibernate solo valida (`ddl-auto: validate`). Toda entidad nueva o cambio de columnas trae su migración en el mismo PR; una migración ya mergeada no se edita. SQL compatible con H2 (`MODE=PostgreSQL`) y con Postgres.
- Lombok está disponible; preferir `record` para DTOs y `@RequiredArgsConstructor` para inyección.
- **Contrato OpenAPI** (ADR 0006): `docs/openapi.json` se genera desde el código y `OpenApiSpecTest` falla si está desactualizado. Si cambiás endpoints/DTOs: `./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true` y después `npm run gen:api` en frontend/. Commiteá ambos archivos. Anotá DTOs con `@Schema(requiredMode = REQUIRED)` en campos obligatorios.

### Frontend (`frontend/src/`)

- `pages/` una pantalla por ruta · `components/` UI reutilizable · `features/<dominio>/` lógica y componentes de un dominio · `services/api.ts` único cliente HTTP · `hooks/` · `types/`.
- Los tipos de la API salen de `src/types/openapi.ts` (generado, no editar) y se exponen con alias en `src/types/api.ts`. No definir DTOs a mano.
- Toda llamada al backend pasa por `services/api.ts` (usa `VITE_API_URL`, default `/api`). No usar `fetch` directo en componentes.
- Rutas en `App.tsx` (React Router, importar de `react-router`). `App` no incluye el router: `main.tsx` usa `BrowserRouter`, los tests `MemoryRouter`.
- Tests con Vitest + Testing Library; mockear `fetch` con `vi.stubGlobal`. Buscar por rol/texto accesible, no por clases.

- **Specs de UI** en [`docs/frontend/`](docs/frontend/README.md): antes de tocar una pantalla o un componente leé `DESIGN-SYSTEM.md` (tokens, componentes, voz), `SCREENS.md` (medidas, textos, estados) y `VIEW-MODES.md` (modo Pantalla/Teléfono, container queries). **No llamar al MCP de Figma** (tiene límite de uso); si hace falta, un solo frame por ID (ver `DESIGN-SYSTEM.md` §10). Estilos solo con `var(--…)`, nunca hex sueltos; layout con `@container`, nunca `@media` de ancho. Si una spec contradice a `docs/reglas/` o al contrato OpenAPI, mandan estos últimos (p. ej. las franjas de 5 min del wireframe): frená y preguntá.

### CI/CD

- `ci-backend.yml` y `ci-frontend.yml`: PR y push a `main`/`develop`. **No agregar filtros `paths`** (rompe los required checks).
- `deploy-frontend.yml`: `workflow_run` sobre "CI Frontend". Si renombrás el workflow de CI, actualizá el nombre ahí.
- `frontend/vercel.json` contiene el placeholder literal `${BACKEND_URL}`: el workflow lo reemplaza. No lo cambies por una URL real.
- Validá workflows con `actionlint` antes de commitear.

## Convenciones de trabajo

- Ramas: desde `develop` salen `feature/`, `fix/`, `chore/`, `docs/`, `refactor/`, `test/` y `ci/`; `hotfix/` sale de `main`. Nombre: `<prefijo>/<issue>-<kebab-case>` (p. ej. `feature/12-reservar-turno`). Lo valida `branch-policy.yml`. Nunca commitear en `main`/`develop` directamente.
- Conventional Commits, chicos y atómicos: `feat(turn): ...`, `fix(frontend): ...`, `ci: ...`, `docs(adr): ...`.
- **No hacer push** ni abrir PRs sin que lo pida quien está trabajando.
- El trabajo se trackea en GitHub Issues con labels `type:*`, `area:*`, `priority:*` (ver README). Los PRs referencian su issue (`Closes #N`).
- Decisiones de arquitectura → nuevo ADR en `docs/adr/` (usar `template.md`). Algo que bloquea → `docs/bloqueantes.md`.
- Al fijar versiones de dependencias o actions, verificá la última versión real (Maven Central, npm, GitHub releases); no uses versiones de memoria.
- Spring Boot 4.1 + Java 25 (ver ADR 0011). Boot 4 usa Jackson 3 (`tools.jackson`) y starters modulares: para testear una tecnología sumá su starter `*-test` (p. ej. `spring-boot-starter-data-jpa-test`).

## Agentes disponibles (`.claude/agents/`)

- `backend-dev` — implementa features del backend siguiendo esta estructura.
- `frontend-dev` — implementa pantallas/features del frontend.
- `test-writer` — escribe o completa tests (JUnit/MockMvc y Vitest/RTL).
- `code-reviewer` — revisa un diff contra las convenciones del repo (solo lectura).
- `devops` — workflows de GitHub Actions, Vercel, Docker.
- `docs-keeper` — ADRs, `bloqueantes.md` y README.
