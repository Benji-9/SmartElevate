# 0004. Migraciones de base de datos con Flyway

- **Estado:** Propuesto
- **Fecha:** 2026-09-28

## Contexto

En `dev` usamos H2 con `ddl-auto=create-drop`. En `prod` (Postgres) el esqueleto deja `ddl-auto=validate`, que falla si el schema no existe. Todavía no hay entidades, así que hoy no hay problema, pero lo habrá con la primera.

Usar `ddl-auto=update` en producción es cómodo pero no borra columnas, no maneja renombres y no deja historial.

## Opciones consideradas

1. `ddl-auto=update` en prod — cero esfuerzo, schema impredecible.
2. **Flyway** — scripts SQL versionados (`db/migration/V1__...sql`), integración nativa con Spring Boot.
3. Liquibase — más potente, más verboso (XML/YAML); overkill para el MVP.

## Decisión (propuesta)

Agregar `flyway-core` + `flyway-database-postgresql` junto con la **primera entidad JPA**, mantener `ddl-auto=validate` en prod, y en dev decidir entre seguir con `create-drop` o correr también Flyway sobre H2 (`MODE=PostgreSQL`).

## Consecuencias

- Cada cambio de modelo trae su script de migración en el mismo PR.
- Mientras tanto, para probar localmente con Postgres se usa `JPA_DDL_AUTO=update`.
