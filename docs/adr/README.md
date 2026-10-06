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
| [0004](0004-migraciones-de-base-de-datos.md) | Migraciones de base de datos con Flyway | Aceptado |
| [0005](0005-spring-boot-3-5.md) | Spring Boot 3.5 en lugar de 4.x | Reemplazado por 0011 |
| [0006](0006-contrato-api-openapi-code-first.md) | Contrato de la API con OpenAPI (code-first) | Propuesto |
| [0007](0007-modelo-de-turnos-y-reservas.md) | Modelo de turnos: la franja es una salida de ascensor | Propuesto |
| [0008](0008-check-in-con-qr-rotativo.md) | Check-in con QR rotativo firmado | Propuesto |
| [0009](0009-autenticacion-y-prioridad.md) | Autenticación con email institucional y prioridad validada en el servidor | Propuesto |
| [0010](0010-integridad-de-reservas-tiempo-y-configuracion.md) | Integridad de reservas, manejo del tiempo y configuración | Propuesto |
| [0011](0011-spring-boot-4-y-java-25.md) | Spring Boot 4.1 y Java 25 | Aceptado |
| [0012](0012-hosting-del-backend-render-y-neon.md) | Hosting del backend en Render y Postgres en Neon | Propuesto |
