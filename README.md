# SmartElevate

App para reducir la congestión de los ascensores del campus de UADE. Los alumnos reservan **turnos por franja horaria** con capacidad limitada (10 personas por turno), hay **usuarios prioritarios** (movilidad reducida, docentes) y una capa de **estadísticas / KPIs de congestión**.

> Estado: **MVP casi completo, sin deploy de producción del backend todavía**. Qué entra, qué queda para la Fase 2 y el estado por funcionalidad: [alcance del MVP](docs/alcance-mvp.md).

## Estado del proyecto

- **Hecho** (backend y pantallas contra la API real): cuentas con email institucional, login con refresh en cookie HttpOnly, olvidé mi contraseña, reservas con cupo atómico y prioritario, cancelación, check-in con QR en kiosco web, no-shows y suspensión, notificaciones por email, ascensor fuera de servicio, KPIs y panel de administración (prioritarios, kioscos, ascensores, simulador).
- **Pendiente**: certificado de movilidad reducida (la UI del perfil corre con datos simulados, [#122](https://github.com/Benji-9/SmartElevate/issues/122), [#123](https://github.com/Benji-9/SmartElevate/issues/123)), deploy del backend en Render ([#222](https://github.com/Benji-9/SmartElevate/issues/222)) y los [bloqueantes](docs/bloqueantes.md) abiertos (conexiones accesibles, datos de salud, calendario de UADE).
- **Fuera del MVP**: lista de espera, envejecimiento de prioridad, edificio alternativo, tolerancia X, ingreso manual del código y encuesta de espera.
- Mapa de pantallas y qué está conectado: [docs/frontend/SCREENS.md](docs/frontend/SCREENS.md#estado-por-pantalla).

## Stack

| Capa | Tecnología |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1, Maven (wrapper), Spring Data JPA, Flyway, H2 (dev) / PostgreSQL (prod), springdoc-openapi |
| Frontend | React 19, Vite 8, TypeScript, React Router, Vitest + React Testing Library, ESLint + Prettier |
| CI/CD | GitHub Actions |
| Deploy | Frontend en Vercel (vía GitHub Actions + Vercel CLI), backend en Render (Docker) y Postgres en Neon. Accesos y operación en [`docs/infraestructura.md`](docs/infraestructura.md) |

## Estructura

```
.
├── backend/                  API REST (Spring Boot)
│   ├── src/main/java/com/smartelevate/
│   │   ├── common/           config, errores, mail, security, time, validation, /api/ping
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
├── docs/                     reglas, ADRs, bloqueantes, specs de UI (frontend/)
├── .github/                  workflows, PR template, dependabot
├── .claude/                  agentes de Claude Code para el equipo
├── CLAUDE.md                 contexto del repo para Claude Code
└── docker-compose.yml        Postgres local
```

## Requisitos

- **JDK 25** (Temurin recomendado; el proyecto compila con `--release 25`)
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
./mvnw spring-boot:run                     # Flyway crea el schema al arrancar
```

#### Variables de entorno del backend

En producción las carga el equipo en Render; la checklist completa (quién la define, cómo generar cada secreto, qué pasa si falta) está en [`docs/infraestructura.md`](docs/infraestructura.md#checklist-maestra-de-variables-de-producción).

| Variable | Default | Descripción |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` (H2) o `prod` (Postgres) |
| `PORT` | `8080` | Puerto HTTP |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` solo en dev | Orígenes permitidos, separados por coma y **exactos**: sin barra final y en minúsculas (`https://smart-elevate.vercel.app`). **Obligatorio en `prod`** (sin default: la app no arranca sin él) |
| `APP_TIME_ZONE` | `America/Argentina/Buenos_Aires` | Zona del campus para franjas y horario. No depende de la zona del servidor (Render corre en UTC) |
| `JWT_SECRET` | — | Secreto de firma de los access tokens (HS256), mínimo 32 caracteres. **Obligatorio en `prod`** (la app no arranca sin él); en dev, si falta, se usa una clave efímera. Generalo con `openssl rand -base64 48`: corré el comando y pegá el **resultado**, nunca el comando. Distinto de `APP_KIOSK_QR_SECRET`; un ejemplo, un placeholder o un valor de baja entropía impide el arranque. Nunca en el repo |
| `JWT_ACCESS_TTL` | `PT15M` | Vida del access token (duración ISO-8601) |
| `JWT_REFRESH_TTL` | `P7D` | Vida del refresh token (duración ISO-8601); también el `Max-Age` de su cookie |
| `APP_REFRESH_COOKIE_SECURE` | `true` (`false` en dev) | Atributo `Secure` de la cookie del refresh; solo se apaga para `http://localhost`. `false` solo vale en dev: en `prod` con `false` la app no arranca |
| `APP_REFRESH_COOKIE_NAME` / `_SAME_SITE` / `_PATH` | `refresh_token` / `Lax` / `/api/auth` | Nombre y atributos de la cookie del refresh (ADR 0019, 0023) |
| `APP_LOGIN_THROTTLE_WINDOW` | `PT15M` | Ventana deslizante del límite de logins fallidos (duración ISO-8601) |
| `APP_LOGIN_THROTTLE_MAX_FAILURES_PER_ACCOUNT` | `10` | Logins fallidos por cuenta dentro de la ventana; entre 1 y 100 (NIST), si no la app no arranca |
| `APP_LOGIN_THROTTLE_MAX_FAILURES_PER_IP` | `100` | Logins fallidos por IP dentro de la ventana |
| `APP_VERIFICATION_TOKEN_TTL` | `PT24H` | Vida del link de verificación de email |
| `APP_VERIFICATION_RESEND_COOLDOWN` | `PT1M` | Espera mínima entre reenvíos del mail de verificación |
| `APP_PASSWORD_RESET_TOKEN_TTL` | `PT30M` | Vida del link de reset de contraseña |
| `APP_PASSWORD_RESET_REQUEST_COOLDOWN` | `PT1M` | Espera mínima entre pedidos de reset de contraseña |
| `DATABASE_URL` | — | Solo `prod`. Formato JDBC: `jdbc:postgresql://host:5432/db` |
| `DATABASE_USER` | — | Solo `prod` |
| `DATABASE_PASSWORD` | — | Solo `prod` |
| `APP_MAIL_ENABLED` | `false` en dev, `true` en `prod` | `true` manda los mails por SMTP; `false` solo los loguea (el link de verificación aparece en el log) |
| `APP_MAIL_FROM` | `no-reply@localhost` | Remitente de los mails |
| `APP_MAIL_ASYNC_THREADS` | `2` | Hilos del executor de los mails masivos (aviso de ascensor fuera de servicio, #134) |
| `APP_MAIL_ASYNC_QUEUE` | `50` | Cola de ese executor. Si se llena, el envío corre en el hilo que lo pidió (no se pierden avisos) |
| `APP_MAIL_SMTP_CONNECTION_TIMEOUT` | `5000` | Solo `prod`. Milisegundos para conectar al SMTP |
| `APP_MAIL_SMTP_TIMEOUT` | `10000` | Solo `prod`. Milisegundos de espera de lectura del SMTP |
| `APP_MAIL_SMTP_WRITE_TIMEOUT` | `10000` | Solo `prod`. Milisegundos de espera de escritura al SMTP |
| `SPRING_MAIL_HOST` | — | **Obligatoria con el mail activo** (default de `prod`): sin ella la app no arranca. Brevo: `smtp-relay.brevo.com` |
| `SPRING_MAIL_PORT` | — | Brevo: `587` (STARTTLS) |
| `SPRING_MAIL_USERNAME` | — | Login SMTP de Brevo |
| `SPRING_MAIL_PASSWORD` | — | SMTP key de Brevo (secreto: solo en el servicio, nunca en el repo) |
| `APP_FRONTEND_URL` | `http://localhost:5173` solo en dev | **Obligatorio en `prod`** (sin default: la app no arranca sin él). Base de los links de los mails: verificación (`<url>/verificar-email?token=…`) y reset de contraseña (`<url>/recuperar/nueva?token=…`) |
| `APP_KIOSK_QR_SECRET` | — | Clave HMAC-SHA256 del token del QR ([ADR 0020](docs/adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)), mínimo 32 caracteres y distinta de `JWT_SECRET`. **Obligatoria en `prod`** (la app no arranca sin ella); en dev, si falta, se usa una efímera. Generala con `openssl rand -base64 48` (el **resultado**, nunca el comando) y que no sea un ejemplo ni baja entropía: si lo es, no arranca. Rotarla invalida los QR en pantalla, que se renuevan solos en el ciclo siguiente. Nunca en el repo |
| `APP_KIOSK_QR_TTL` | `PT30S` | Vida de cada token del QR (segundos enteros); se acepta también el del ciclo anterior. Reemplaza a `qr_rotation` de `app_config` |
| `APP_KIOSK_SIMULATOR_ENABLED` | `false` | `true` permite crear kioscos simulados (simulador de admin); sus check-ins cuentan como simulados. **Dejarlo apagado en prod** |
| `APP_KIOSK_RATE_LIMIT_MAX_REQUESTS` | `10` | Pedidos de token por kiosco dentro de la ventana (se espera ~1 cada 30 s); al pasarse, 429 con `Retry-After` |
| `APP_KIOSK_RATE_LIMIT_WINDOW` | `PT1M` | Ventana del límite de pedidos por kiosco |
| `APP_ADMIN_USER_SEARCH_MIN_QUERY_LENGTH` / `_PAGE_SIZE` | `3` / `20` | Buscador de usuarios del panel admin: mínimo de caracteres (≥ 2) y tamaño de página (1 a 100) |
| `APP_SCHEDULING_ENABLED` | `true` | `false` apaga todos los jobs `@Scheduled` (no-shows y recordatorios); lo usan los tests |
| `APP_NO_SHOW_JOB_INTERVAL` | `PT15S` | Cada cuánto se marcan los no-shows |
| `APP_REMINDER_JOB_ENABLED` / `_INTERVAL` | `true` / `PT30S` | Recordatorio de salida por mail; `false` lo desactiva (p. ej. sin SMTP) |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75` | Solo en la imagen Docker. Si Render reinicia por memoria, `-XX:MaxRAMPercentage=60` |

**Checklist de `prod`.** Sin estas variables (o con alguna en blanco) la app no arranca:

- [ ] `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`
- [ ] `JWT_SECRET` (mínimo 32 caracteres; generada con `openssl rand -base64 48`, nunca copiada de un ejemplo)
- [ ] `APP_FRONTEND_URL` (p. ej. `https://smart-elevate.vercel.app`)
- [ ] `CORS_ALLOWED_ORIGINS` (al menos el dominio del front)
- [ ] `APP_KIOSK_QR_SECRET` (mínimo 32 caracteres, **distinta** de `JWT_SECRET` y generada, nunca copiada de un ejemplo; clave HMAC del QR, #229)

- [ ] `SPRING_MAIL_HOST` (con el mail activo, el default en `prod`; más `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` y `APP_MAIL_FROM` para que los mails salgan)

La app no arranca sin `SPRING_MAIL_HOST` si el mail está activo; sin las demás `SPRING_MAIL_*` arranca pero los mails fallan. `JWT_SECRET`, `APP_FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` y `APP_KIOSK_QR_SECRET` se validan al arrancar en `RequiredProdProperties` (#222), que además rechaza las dos claves si son iguales, si son un ejemplo/placeholder (p. ej. el texto `openssl rand -base64 48`) o de baja entropía, y `APP_REFRESH_COOKIE_SECURE=false`. Después de deployar, correr [`scripts/smoke-test.sh`](scripts/smoke-test.sh) ([guía](docs/infraestructura.md#smoke-test-del-deploy)).

#### Autenticación y endpoints protegidos

`POST /api/auth/login` devuelve un access token (JWT, 15 min, solo con `sub`: **sin rol ni prioridad**) y un refresh token opaco de un solo uso (se guarda hasheado) que va **solo** en una cookie `HttpOnly; Secure; SameSite=Lax` (`Path=/api/auth`), no en el body (ADR 0019). `POST /api/auth/refresh` lo lee de la cookie y lo rota; si un refresh ya rotado reaparece, se revoca toda la sesión. `POST /api/auth/logout` lo revoca y expira la cookie. Ambos exigen el header `X-Requested-With: SmartElevate` y, si viene `Origin`, que esté en `CORS_ALLOWED_ORIGINS` (defensa CSRF, ADR 0023). `CORS_ALLOWED_ORIGINS` se compara **exacto** con el `Origin`: sin barra final y en minúsculas (`https://smart-elevate.vercel.app`). Un refresh inválido responde 401 y además expira la cookie. El rol se lee de la base en cada request y la prioridad se consulta al reservar (ADR 0009). No se puede loguear una cuenta con el email sin verificar.

Públicos: `/api/ping`, `/api/auth/**`, `GET /api/elevators`, `GET /api/buildings`, la documentación (`/v3/api-docs`, `/swagger-ui.html`) y `/actuator/health`. **Todo lo demás exige `Authorization: Bearer <accessToken>`**. Para agregar un endpoint:

- Protegido: no hay que hacer nada, es el default. Por rol, `@PreAuthorize("hasRole('ADMIN')")` en el método (sin token da 401, con otro rol 403).
- Usuario actual: parámetro `@AuthenticationPrincipal CurrentUser user` (`common/security`).
- Documentarlo en OpenAPI: `@SecurityRequirement(name = OpenApiConfig.BEARER)` en el controller.
- Público: sumarlo a `SecurityConfig.PUBLIC_ANY` / `PUBLIC_GET`, solo con motivo.
- Tests `@WebMvcTest`: anotar con `@WithAppSecurity` (carga las reglas reales) y usar `jwt()` de `spring-security-test`.

Roles y prioridad (#121): `GET/POST /api/admin/teachers/...`, `GET/DELETE /api/admin/priority-users/...` y `PUT /api/admin/users/{id}/role` son solo ADMIN. La prioridad vigente (aprobada y no vencida) la da `PriorityService.currentPriority(userId)`, el único punto que debe usar la reserva; cada cambio queda en la tabla `priority_audit`. Cómo crear el primer ADMIN: [`docs/infraestructura.md`](docs/infraestructura.md#crear-el-primer-admin).

Kioscos del QR (#229, [ADR 0020](docs/adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)): `GET/POST /api/admin/kiosks` y `POST /api/admin/kiosks/{id}/revoke` son solo ADMIN; el alta devuelve el secreto del kiosco **una sola vez**. `/api/kiosks/**` tiene su propia cadena de seguridad (`KioskSecurityConfig`): se autentica con `Authorization: Bearer <secreto del kiosco>`, no con un JWT. Las altas y revocaciones quedan en `priority_audit`.

#### Migraciones de la base (Flyway)

El schema lo crean los scripts de `backend/src/main/resources/db/migration/`, igual en dev (H2), en los tests y en prod (Postgres). Hibernate solo valida que las entidades coincidan (`ddl-auto: validate`). Decisión en el [ADR 0004](docs/adr/0004-migraciones-de-base-de-datos.md).

- Un script por cambio, con nombre `V<n>__<descripcion>.sql` (p. ej. `V2__agregar_ascensor_a_reservas.sql`), en el mismo PR que el cambio de entidades.
- **Una migración ya mergeada no se edita**: Flyway detecta el cambio y la app no arranca. Se corrige con una migración nueva.
- SQL compatible con H2 y con Postgres: sin tipos ni funciones exclusivas de Postgres.
- Si ya tenías un Postgres local con tablas creadas por Hibernate, recrealo con `docker compose down -v` y volvé a levantarlo.

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

En dev, Vite hace proxy de `/api` a `http://localhost:8080`.

| Variable | Default | Descripción |
| --- | --- | --- |
| `VITE_API_URL` | `/api` | Base del cliente HTTP (`services/api.ts`) |
| `VITE_USE_MOCKS` | `true` en `npm run dev`, `false` en tests | `true` usa datos simulados en lugar del backend (`services/mocks.ts`); con `false`, `npm run dev` le pega al backend real. Nunca aplica al build de producción |

## Contrato de la API (OpenAPI / Swagger)

La documentación de los endpoints se genera sola desde el código (springdoc) y queda versionada en [`docs/openapi.json`](docs/openapi.json). Detalle en el [ADR 0006](docs/adr/0006-contrato-api-openapi-code-first.md).

- **Swagger UI:** http://localhost:8080/swagger-ui.html — ver y probar cada endpoint ("Try it out").
- **Spec OpenAPI (JSON):** http://localhost:8080/v3/api-docs
- **Tipos TypeScript:** `frontend/src/types/openapi.ts`, generados desde la spec. Se usan vía alias en `src/types/api.ts`.

Cuando agregás o cambiás un endpoint o un DTO:

```bash
cd backend
./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true   # regenera docs/openapi.json
cd ../frontend
npm run gen:api                                            # regenera src/types/openapi.ts
```

Commiteá los dos archivos en el mismo PR. Si te olvidás, falla el CI (`backend-verify` o `frontend-verify`) con el comando a correr.

Documentá con anotaciones: `@Tag` y `@Operation` en el controller, `@Schema` en los DTOs (`requiredMode = REQUIRED` para campos obligatorios) y Bean Validation (`@NotNull`, `@Size`), que también aparece en la spec.

## Tests, lint y formato

| | Comando |
| --- | --- |
| Backend: build + tests | `cd backend && ./mvnw verify` |
| Frontend: tests (una pasada) | `cd frontend && npm test -- --run` |
| Frontend: tests en watch | `npm test` |
| Frontend: lint | `npm run lint` |
| Frontend: formatear | `npm run format` (chequear sin escribir: `npm run format:check`) |
| Frontend: build | `npm run build` |
| Frontend: regenerar tipos de la API | `npm run gen:api` |
| Backend: regenerar `docs/openapi.json` | `./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true` |

Lo mismo que corre el CI del frontend: `npm ci && npm run lint && npm test -- --run && npm run build`.

**Tests del backend contra Postgres real.** Casi todos los tests usan H2, pero los que dependen de Postgres (migraciones, locks del cupo) levantan un `postgres:18-alpine` en Docker con [Testcontainers](https://java.testcontainers.org/). Necesitan Docker corriendo (Docker Desktop en Windows/macOS); **sin Docker se saltean** (salen como *skipped*) y `./mvnw verify` sigue pasando. En CI siempre corren. Para un test nuevo: `@Import(PostgresTestcontainersConfig.class)` + `@Testcontainers(disabledWithoutDocker = true)` (ver `FlywayPostgresTest`).

## Estrategia de ramas

```
main        ← producción (deploy automático a Vercel prod)
  ↑ PR (merge commit)        ↑ PR
develop     ← integración    hotfix/*  ← urgencias en producción (sale de main)
  ↑ PR (squash)
feature/* · fix/* · chore/* · docs/* · refactor/* · test/* · ci/*   (salen de develop)
```

| Prefijo | Para qué | Sale de → vuelve a | Label automático |
| --- | --- | --- | --- |
| `feature/` | Funcionalidad nueva | `develop` → `develop` | `type: feature` |
| `fix/` | Bug encontrado en `develop` | `develop` → `develop` | `type: bug` |
| `chore/` | Dependencias, config, tooling | `develop` → `develop` | `type: chore` |
| `docs/` | Solo documentación | `develop` → `develop` | `type: docs` |
| `refactor/` | Cambio interno sin impacto funcional | `develop` → `develop` | `type: refactor` |
| `test/` | Tests nuevos o ajustados | `develop` → `develop` | `type: test` |
| `ci/` | Workflows, deploy | `develop` → `develop` | `type: ci-cd` |
| `hotfix/` | Bug urgente en producción | `main` → `main` y después `develop` | `type: bug`, `priority: high` |

**Nombre:** `<prefijo>/<numero-de-issue>-<descripcion-en-kebab-case>`, en minúsculas. Ej.: `feature/12-reservar-turno`, `fix/27-cupo-negativo`.

```bash
git switch develop && git pull
git switch -c feature/12-reservar-turno
# ... commits ...
git push -u origin feature/12-reservar-turno   # y abrir PR contra develop
```

**Flujos:**

- **Trabajo diario:** rama desde `develop` → PR a `develop` → merge con **squash** (un commit por PR, con el título del PR).
- **Release:** PR `develop → main` → merge con **merge commit** (no squash, para que ambas ramas compartan historia). Dispara el deploy de producción.
- **Hotfix:** rama `hotfix/*` desde `main` → PR a `main` → después, PR `main → develop` para no perder el arreglo.

**Reglas (automáticas):**

- `main` y `develop` están protegidas: nadie pushea directo, todo entra por PR con los checks en verde (`backend-verify`, `frontend-verify`, `branch-name`), rama al día y conversaciones resueltas. La review **no es obligatoria**: GitHub se la pide automáticamente a los code owners ([`.github/CODEOWNERS`](.github/CODEOWNERS)).
- El workflow [`branch-policy.yml`](.github/workflows/branch-policy.yml) falla el PR si la rama no respeta la convención o si a `main` llega algo que no sea `develop` o `hotfix/*`, y etiqueta el PR según el prefijo.

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
| [`ci-backend.yml`](.github/workflows/ci-backend.yml) | PR y push a `main`/`develop` | Temurin 25 + cache Maven, `./mvnw -B verify`, sube los reportes de tests como artifact |
| [`ci-frontend.yml`](.github/workflows/ci-frontend.yml) | PR y push a `main`/`develop` | Node 24 + cache npm, `npm ci`, lint, tests, build |
| [`branch-policy.yml`](.github/workflows/branch-policy.yml) | PR a `main`/`develop` | Valida el nombre y el destino de la rama y agrega el label `type:` según el prefijo |
| [`deploy-frontend.yml`](.github/workflows/deploy-frontend.yml) | Cuando **CI Frontend** termina OK (`workflow_run`) | PR → preview + comentario con la URL · push a `develop` → preview · push a `main` → producción |
| [`keep-alive-backend.yml`](.github/workflows/keep-alive-backend.yml) | Cada 10 min, 07:00–22:59 (Buenos Aires) | `GET /api/ping` para que Render no duerma el backend ([detalle](docs/infraestructura.md#keep-alive)) |

Detalles:

- Los CI no usan filtros `paths`, así pueden configurarse como **required checks** sin quedar en "pending".
- Cada workflow tiene `concurrency` (cancela corridas viejas del mismo PR/rama; nunca un deploy de producción) y `permissions` mínimos.
- El deploy nunca corre para PRs de forks (el workflow tiene acceso a secrets).
- Los deploys automáticos de Vercel por Git están apagados (`"git": { "deploymentEnabled": false }` en `vercel.json`): **el único camino de deploy es el workflow**.
- Decisiones y trade-offs: [ADR 0003](docs/adr/0003-deploy-frontend-vercel-via-github-actions.md).
- El backend no tiene workflow de deploy: Render lo buildea solo desde `main` cuando pasa `backend-verify` ([ADR 0012](docs/adr/0012-hosting-del-backend-render-y-neon.md)).

### ¿Cómo llega `/api` al backend en Vercel?

`frontend/vercel.json` tiene un rewrite de `/api/(.*)` hacia `${BACKEND_URL}/api/$1`. Vercel **no** interpola variables en `vercel.json`, así que el workflow reemplaza el placeholder `${BACKEND_URL}` con la variable de GitHub `BACKEND_URL` antes de `vercel build`. Si la variable no está definida, el workflow emite un warning y quita ese rewrite (el front deploya igual, pero sin backend).

## Setup manual pendiente

- **Clave HMAC del QR** ([ADR 0016](docs/adr/0016-qr-firmado-con-hmac-y-kiosco-web.md), [0020](docs/adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)): cargar `APP_KIOSK_QR_SECRET` en Render antes del próximo deploy del backend ([#222](https://github.com/Benji-9/SmartElevate/issues/222)): sin ella no arranca en `prod`.
- **Pantallas del kiosco QR**: conseguir una tablet o pantalla con red por ascensor ([#23](https://github.com/Benji-9/SmartElevate/issues/23)); cada una se da de alta en `/admin/kioscos` y abre `/qr/<ascensor>`.
- **Calendario de cierres de UADE** para `semester_end_dates`, antes del 31/12/2026 ([B-19](docs/bloqueantes.md)).
- **Primer ADMIN** por SQL: [`docs/infraestructura.md`](docs/infraestructura.md#crear-el-primer-admin).

Las previews de Vercel no tienen Deployment Protection: cualquiera con la URL las puede abrir ([bloqueantes](docs/bloqueantes.md), B-07).

## Setup inicial de Vercel y GitHub (ya hecho)

Quedó hecho en B-02, B-03 y B-09 ([bloqueantes](docs/bloqueantes.md)). Se deja como referencia para rearmarlo. Issues con checklist: Vercel [#1](https://github.com/Benji-9/SmartElevate/issues/1) · secrets [#2](https://github.com/Benji-9/SmartElevate/issues/2) · protección de ramas [#3](https://github.com/Benji-9/SmartElevate/issues/3) · primer release [#4](https://github.com/Benji-9/SmartElevate/issues/4).

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
  - `BACKEND_URL` — URL pública del backend, sin `/` final (hoy `https://smartelevate.onrender.com`). Se puede definir distinta por environment (`preview` / `production`) en **Settings → Environments**; los environments los crea el workflow en la primera corrida.

### 4. Vercel: variables de entorno

En el proyecto → **Settings → Environment Variables** (para *Production* y *Preview*):

- `VITE_API_URL` = `/api` (usa el rewrite de `vercel.json`; **hace falta** para que la cookie del refresh sea del mismo origen, ADR 0019). Con la URL absoluta del backend la sesión no se mantiene (cookie cross-site descartada).

`vercel pull` las baja en cada deploy y Vite las embebe en el build.

### 5. GitHub: protección de ramas

**Settings → Branches → Add rule** (o *Rulesets*) para `main` y `develop`:

- Require a pull request before merging (sin aprobaciones obligatorias; las reviews se piden por CODEOWNERS)
- Require status checks to pass: `backend-verify`, `frontend-verify` y `branch-name`
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

- [docs/reglas/](docs/reglas/) — **reglas de negocio**: turnos, asignación, check-in QR, usuarios y prioridad, KPIs y parámetros configurables
- [docs/frontend/](docs/frontend/README.md) — **specs de UI**: design system (tokens y componentes), pantallas y modos de vista Pantalla/Teléfono; reemplazan consultar Figma
- [docs/alcance-mvp.md](docs/alcance-mvp.md) — **alcance del MVP**: qué entra y qué queda para la Fase 2, con el porqué
- [docs/glosario.md](docs/glosario.md) — términos del dominio
- [docs/infraestructura.md](docs/infraestructura.md) — accesos y operación (Render, Neon, Vercel, Brevo, R2)
- [docs/bloqueantes.md](docs/bloqueantes.md) — bloqueantes y decisiones abiertas
- [docs/adr/](docs/adr/README.md) — Architecture Decision Records (índice con estados)
- [docs/openapi.json](docs/openapi.json) — contrato de la API (generado)
- Swagger UI del backend: `/swagger-ui.html`

## Equipo

Proyecto de 6 estudiantes de UADE.
