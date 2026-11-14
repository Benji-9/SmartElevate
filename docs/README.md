# Documentación

Índice de la documentación de SmartElevate, ordenado por para qué la necesitás (modelo [Diátaxis](https://diataxis.fr/)). Todo en español rioplatense. Cada archivo vive donde ya estaba; las carpetas `guias/` y `referencia/` son nuevas.

## Primeros pasos

Para levantar el proyecto y orientarte.

- [README del repo](../README.md): qué es, stack, cómo levantarla en local y árbol de la documentación.
- [Backend](../backend/README.md): requisitos, comandos, estructura, migraciones, tests.
- [Frontend](../frontend/README.md): requisitos, comandos, estructura, convenciones, tests.
- [Cómo contribuir](../CONTRIBUTING.md): ramas, commits, issues y PRs.
- [Glosario](glosario.md): términos del dominio.

## Guías prácticas

Para resolver una tarea concreta.

- [Autenticación y endpoints protegidos](guias/autenticacion-y-endpoints.md): agregar un endpoint público, protegido o por rol.
- [Contrato OpenAPI](../backend/README.md#contrato-openapi): regenerar `openapi.json` y los tipos del frontend.
- [Migraciones de la base](../backend/README.md#migraciones-de-la-base-flyway).
- [Infraestructura y operación](infraestructura.md): Render, Neon, Vercel, Brevo, primer ADMIN, smoke test, rollback.
- [Configurar Vercel y GitHub](guias/configurar-vercel-y-github.md): setup inicial (ya hecho), para rearmarlo.
- [Prueba de usabilidad](frontend/usabilidad.md): plan y guion.
- [Reportar una vulnerabilidad](../SECURITY.md).

## Referencia

Para consultar un dato exacto.

- [Reglas de negocio](reglas/README.md): [turnos](reglas/turnos.md), [asignación](reglas/asignacion.md), [check-in con QR](reglas/check-in-qr.md), [usuarios y prioridad](reglas/usuarios-y-prioridad.md), [KPIs](reglas/kpis.md).
- [Contrato de la API (`openapi.json`)](openapi.json): generado desde el código; Swagger UI en `/swagger-ui.html`.
- [Variables de entorno del backend](referencia/variables-de-entorno.md) (las del frontend, en su [README](../frontend/README.md#variables-de-entorno)).
- [Diagrama entidad-relación (DER)](referencia/der.md): tablas, relaciones y constraints de la base.
- [CI/CD: workflows](referencia/ci-cd.md).
- [Specs de UI](frontend/README.md): [design system](frontend/DESIGN-SYSTEM.md), [pantallas](frontend/SCREENS.md), [modos de vista](frontend/VIEW-MODES.md).

## Explicación y decisiones

Para entender el porqué.

- [Alcance del MVP](alcance-mvp.md): qué entra, qué queda para la Fase 2 y por qué.
- [ADRs](adr/README.md): decisiones de arquitectura, con índice y estados.
- [Bloqueantes](bloqueantes.md): decisiones abiertas y lo que impide avanzar.

## Para agentes de código

[`AGENTS.md`](../AGENTS.md) es el contexto corto (comandos, flujo, reglas que no se negocian). [`CLAUDE.md`](../CLAUDE.md) lo importa y agrega lo propio de Claude Code.

## Mantener la documentación

- Un dato vive en un solo lugar; los demás archivos enlazan. Si movés o renombrás un archivo, actualizá los enlaces.
- Si cambiás comandos, variables de entorno o workflows, actualizá el README del paquete o la referencia que corresponda en el mismo PR.
- Una regla de `reglas/` o un ADR aceptado no se reescribe para "emprolijar": una decisión nueva va en un ADR nuevo.
