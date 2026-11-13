# SmartElevate

App para reducir la congestión de los ascensores del campus de UADE. Los alumnos reservan **turnos por franja horaria** con capacidad limitada (10 personas por turno), hay **usuarios prioritarios** (movilidad reducida, docentes) y una capa de **estadísticas / KPIs de congestión**.

> Estado: **MVP casi completo, sin deploy de producción del backend todavía**. Qué entra, qué queda para la Fase 2 y el estado por funcionalidad: [alcance del MVP](docs/alcance-mvp.md).

## Estado del proyecto

- **Hecho** (backend y pantallas contra la API real): cuentas con email institucional, login con refresh en cookie HttpOnly, olvidé mi contraseña, reservas con cupo atómico y prioritario, cancelación, check-in con QR en kiosco web, no-shows y suspensión, notificaciones por email, ascensor fuera de servicio, KPIs y panel de administración (prioritarios, kioscos, ascensores, simulador).
- **Pendiente**: la integración con OFALU para la marca de movilidad reducida (Fase 2: la app no maneja certificados, [ADR 0024](docs/adr/0024-movilidad-reducida-la-marca-viene-de-ofalu.md)) y el [calendario de cierres de UADE](docs/bloqueantes.md) para los cuatrimestres.
- **Fuera del MVP**: lista de espera, envejecimiento de prioridad, edificio alternativo, tolerancia X, ingreso manual del código y encuesta de espera.
- Mapa de pantallas y qué está conectado: [docs/frontend/SCREENS.md](docs/frontend/SCREENS.md#estado-por-pantalla).

## Stack

| Capa | Tecnología |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1, Maven (wrapper), Spring Data JPA, Flyway, H2 (dev) / PostgreSQL (prod), springdoc-openapi |
| Frontend | React 19, Vite 8, TypeScript, React Router, Vitest + React Testing Library, ESLint + Prettier |
| CI/CD | GitHub Actions |
| Deploy | Frontend en Vercel (vía GitHub Actions + Vercel CLI), backend en Render (Docker) y Postgres en Neon. Accesos y operación en [`docs/infraestructura.md`](docs/infraestructura.md) |

## Inicio rápido

Necesitás **JDK 25** y **Node.js 24 LTS** (`frontend/.nvmrc`). Docker es opcional. No hace falta instalar Maven: se usa `./mvnw` (en Windows, `mvnw.cmd`).

```bash
# Terminal 1: backend (perfil dev, H2 en memoria)
cd backend
./mvnw spring-boot:run        # http://localhost:8080/api/ping → {"status":"ok"}

# Terminal 2: frontend
cd frontend
npm install
npm run dev                   # http://localhost:5173 (Vite hace proxy de /api al backend)
```

Por defecto `npm run dev` usa datos simulados; para pegarle al backend real, `VITE_USE_MOCKS=false` ([detalle](frontend/README.md#variables-de-entorno)). Swagger UI: http://localhost:8080/swagger-ui.html.

Para correr tests, Postgres local, migraciones, variables de entorno e imagen Docker, ver el README de cada paquete.

## Mapa del repo

```
.
├── backend/          API REST (Spring Boot)           → backend/README.md
├── frontend/         SPA (Vite + React + TS)          → frontend/README.md
├── docs/             reglas, ADRs, referencia, guías, specs de UI → docs/README.md
├── scripts/          smoke-test.sh del backend desplegado
├── .github/          workflows, PR template, issue forms, dependabot, CODEOWNERS
├── .claude/          agentes de Claude Code para el equipo
├── AGENTS.md         contexto del repo para agentes de código (CLAUDE.md lo importa)
├── CONTRIBUTING.md   ramas, commits, issues, PRs
├── SECURITY.md       cómo reportar una vulnerabilidad
└── docker-compose.yml   Postgres local
```

## Documentación

Índice completo, ordenado por propósito: [`docs/README.md`](docs/README.md). Lo más usado:

- [Reglas de negocio](docs/reglas/README.md) y [glosario](docs/glosario.md)
- [Contribuir](CONTRIBUTING.md): ramas, commits, PRs, automerge
- [Variables de entorno](docs/referencia/variables-de-entorno.md) y [CI/CD](docs/referencia/ci-cd.md)
- [Infraestructura y operación](docs/infraestructura.md)
- [ADRs](docs/adr/README.md) y [bloqueantes](docs/bloqueantes.md)
- [Contrato de la API](docs/openapi.json) (Swagger UI en `/swagger-ui.html`)

## Setup manual pendiente

- **Pantallas del kiosco QR**: conseguir una tablet o pantalla con red por ascensor ([#23](https://github.com/Benji-9/SmartElevate/issues/23)); cada una se da de alta en `/admin/kioscos` y abre `/qr/<ascensor>`.
- **Calendario de cierres de UADE** para `semester_end_dates`, antes del 31/12/2026 ([B-19](docs/bloqueantes.md)).
- **Primer ADMIN** por SQL: [`docs/infraestructura.md`](docs/infraestructura.md#crear-el-primer-admin).

Las previews de Vercel no tienen Deployment Protection: cualquiera con la URL las puede abrir ([bloqueantes](docs/bloqueantes.md), B-07).

El setup inicial de Vercel y GitHub ya está hecho; queda como referencia en [Configurar Vercel y GitHub](docs/guias/configurar-vercel-y-github.md).

## Equipo

Proyecto de 6 estudiantes de UADE.
