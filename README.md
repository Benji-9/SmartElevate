# SmartElevate

## Qué es

App para reducir la congestión de los ascensores del campus de UADE. Cada salida de ascensor (~2 min) es una franja con **cupo de 10 personas**: el alumno elige origen, destino y hora, y el servidor le asigna la salida. Hay **usuarios prioritarios** (movilidad reducida, docentes), **check-in con QR** rotativo en un kiosco del hall y **KPIs de congestión** para el panel de administración. La usan alumnos y docentes (reservan) y revisores y administradores (prioridades, kioscos, ascensores, KPIs). MVP de 6 estudiantes de UADE; estado y alcance en [docs/alcance-mvp.md](docs/alcance-mvp.md).

## Stack

| Capa | Tecnología |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1, Maven (wrapper), Spring Data JPA, springdoc-openapi |
| Frontend | React 19, Vite 8, TypeScript 6, React Router 8, Vitest + Testing Library |
| Base de datos | H2 en memoria (dev) y PostgreSQL 18 (prod, en Neon), migraciones con Flyway |
| CI/CD | GitHub Actions |
| Deploy | Frontend en Vercel, backend en Render (Docker): [infraestructura](docs/infraestructura.md) |

## Levantarla en local

Requisitos: **JDK 25**, **Node.js 24** (`frontend/.nvmrc`) y, opcional, Docker. No hace falta instalar Maven: se usa `./mvnw` (en Windows, `mvnw.cmd`).

```bash
# Terminal 1: backend (perfil dev, H2 en memoria)
cd backend && ./mvnw spring-boot:run   # http://localhost:8080/api/ping, Swagger en /swagger-ui.html

# Terminal 2: frontend (con datos simulados por defecto)
cd frontend && npm ci && npm run dev   # http://localhost:5173
```

Para que el frontend use el backend real: `VITE_USE_MOCKS=false npm run dev` ([detalle](frontend/README.md#variables-de-entorno)). Postgres local, opcional: `docker compose up -d` y las variables del perfil `prod` ([pasos](backend/README.md#con-postgres-perfil-prod)).

## Documentación

- [docs/README.md](docs/README.md): índice completo
- Primeros pasos
  - [CONTRIBUTING.md](CONTRIBUTING.md): ramas, commits, issues, PRs y automerge
  - [backend/README.md](backend/README.md): comandos, estructura, migraciones, tests
  - [frontend/README.md](frontend/README.md): comandos, estructura, convenciones, tests
  - [docs/glosario.md](docs/glosario.md): términos del dominio
  - [docs/alcance-mvp.md](docs/alcance-mvp.md): qué entra en el MVP y qué queda para la Fase 2
- Reglas de negocio
  - [docs/reglas/](docs/reglas/README.md): turnos, asignación, check-in con QR, usuarios y prioridad, KPIs
- Decisiones
  - [docs/adr/](docs/adr/README.md): decisiones de arquitectura (ADRs)
  - [docs/bloqueantes.md](docs/bloqueantes.md): decisiones abiertas y setup manual pendiente (kiosco QR, calendario de cuatrimestres, previews de Vercel)
- Referencia
  - [docs/referencia/der.md](docs/referencia/der.md): diagrama entidad-relación de la base
  - [docs/openapi.json](docs/openapi.json): contrato de la API (generado)
  - [docs/referencia/variables-de-entorno.md](docs/referencia/variables-de-entorno.md): variables del backend y checklist de `prod`
  - [docs/referencia/ci-cd.md](docs/referencia/ci-cd.md): workflows de GitHub Actions
- Guías y operación
  - [docs/guias/autenticacion-y-endpoints.md](docs/guias/autenticacion-y-endpoints.md): endpoints públicos, protegidos o por rol
  - [docs/infraestructura.md](docs/infraestructura.md): Render, Neon, Vercel, Brevo, primer ADMIN, smoke test, rollback
  - [docs/guias/configurar-vercel-y-github.md](docs/guias/configurar-vercel-y-github.md): setup inicial de Vercel y GitHub
  - [SECURITY.md](SECURITY.md): cómo reportar una vulnerabilidad
- UI
  - [docs/frontend/](docs/frontend/README.md): design system, pantallas, modos de vista y prueba de usabilidad
- Agentes de código
  - [AGENTS.md](AGENTS.md): contexto del repo (comandos, flujo, reglas que no se negocian)
