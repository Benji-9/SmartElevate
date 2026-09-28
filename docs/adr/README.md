# Architecture Decision Records (ADR)

Cada decisión de arquitectura o de proceso que sea difícil de revertir o que valga la pena explicar a alguien nuevo se registra acá, en un archivo corto e inmutable.

## Cómo agregar uno

1. Copiar [`template.md`](template.md) como `NNNN-titulo-en-kebab-case.md` (número siguiente).
2. Completar contexto, decisión y consecuencias. Estado inicial: **Propuesto**.
3. Abrir PR (`docs(adr): ...`). Al mergearse con acuerdo del equipo pasa a **Aceptado**.
4. Si una decisión cambia, **no se edita** el ADR viejo: se crea uno nuevo y el viejo pasa a **Reemplazado por NNNN**.

## Índice

| # | Título | Estado |
| --- | --- | --- |
| [0001](0001-registrar-decisiones-con-adrs.md) | Registrar decisiones con ADRs | Aceptado |
| [0002](0002-monorepo-backend-frontend.md) | Monorepo con backend y frontend | Aceptado |
| [0003](0003-deploy-frontend-vercel-via-github-actions.md) | Deploy del frontend a Vercel vía GitHub Actions | Aceptado |
| [0004](0004-migraciones-de-base-de-datos.md) | Migraciones de base de datos con Flyway | Propuesto |
| [0005](0005-spring-boot-3-5.md) | Spring Boot 3.5 en lugar de 4.x | Aceptado |
| [0006](0006-contrato-api-openapi-code-first.md) | Contrato de la API con OpenAPI (code-first) | Propuesto |
