# 0004. Migraciones de base de datos con Flyway

- **Estado:** Aceptado
- **Fecha:** 2026-09-28 (propuesto), 2026-10-05 (aceptado)
- **Issue:** [#9](https://github.com/Benji-9/SmartElevate/issues/9)

## Contexto

En `dev` usamos H2 con `ddl-auto=create-drop`. En `prod` (Postgres) el esqueleto deja `ddl-auto=validate`, que falla si el schema no existe. Todavía no hay entidades, así que hoy no hay problema, pero lo habrá con la primera.

Usar `ddl-auto=update` en producción es cómodo pero no borra columnas, no maneja renombres y no deja historial.

## Opciones consideradas

1. `ddl-auto=update` en prod — cero esfuerzo, schema impredecible.
2. **Flyway** — scripts SQL versionados (`db/migration/V1__...sql`), integración nativa con Spring Boot.
3. Liquibase — más potente, más verboso (XML/YAML); overkill para el MVP.

Para `dev` (H2), una vez elegido Flyway:

- **a. Seguir con `create-drop`**: cómodo, pero las migraciones solo se probarían contra Postgres y el schema de dev podría divergir del de prod.
- **b. Correr también Flyway sobre H2** (`MODE=PostgreSQL`): el schema es el mismo en todos lados, a costa de escribir SQL compatible con las dos bases.

## Decisión

Flyway en **todos los perfiles** (dev, tests y prod), con `ddl-auto=validate` en todos: Hibernate solo verifica que las entidades coincidan con el schema que crean las migraciones.

- Dependencias: `spring-boot-starter-flyway` (trae `flyway-core`; versión administrada por el BOM de Boot) y `flyway-database-postgresql` (desde Flyway 10, Postgres es un módulo aparte; H2 viene en `flyway-core`).
- Migraciones en `backend/src/main/resources/db/migration/V<n>__<descripcion>.sql`. Una migración ya mergeada no se edita: los cambios van en una nueva.
- SQL compatible con H2 (`MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`) y con Postgres: sin tipos ni funciones exclusivas de Postgres.
- `spring.flyway.clean-disabled=true` en todos los perfiles.
- La primera migración llega con la primera entidad e incluye la tabla de configuración ([ADR 0010](0010-integridad-de-reservas-tiempo-y-configuracion.md)).

## Consecuencias

- Cada cambio de modelo trae su script de migración en el mismo PR. Ya no se usa `JPA_DDL_AUTO`.
- El schema es el mismo en dev, tests y prod, y los cambios se revisan en el PR.
- Si una entidad no coincide con el schema, la app no arranca (`validate`), también en dev.
- Si dos ramas crean la misma versión (`V3`), la que se mergea después la renumera.
- Si en algún momento hace falta algo exclusivo de Postgres (`jsonb`, índices parciales), habrá que probar las migraciones contra un Postgres real (Testcontainers, ver ADR 0010).
- H2 2.4.x supera la versión que Flyway 12.4 tiene verificada (2.3.232): Flyway lo avisa con un WARN al arrancar y por ahora no genera problemas.
- Una base que ya tenga tablas creadas por Hibernate (p. ej. un Postgres local donde se usó `update`) hay que recrearla (`docker compose down -v`) o hacerle baseline.
