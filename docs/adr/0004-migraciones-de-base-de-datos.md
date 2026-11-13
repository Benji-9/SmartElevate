# ADR-0004: Migraciones de base de datos con Flyway

## Metadata

- **Status:** Accepted
- **Date:** 2026-09-28 (propuesto), 2026-10-05 (aceptado)
- **Deciders:** Equipo SmartElevate
- **Owner:** No figura en el ADR original
- **Related Issues:** [#9](https://github.com/Benji-9/SmartElevate/issues/9)
- **Related ADRs:** [ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0004-migraciones-de-base-de-datos.md`)

---

## 1. Context and Problem Statement

### Context

En `dev` usamos H2 con `ddl-auto=create-drop`. En `prod` (Postgres) el esqueleto deja `ddl-auto=validate`, que falla si el schema no existe. Todavía no hay entidades, así que hoy no hay problema, pero lo habrá con la primera.

Usar `ddl-auto=update` en producción es cómodo pero no borra columnas, no maneja renombres y no deja historial.

### Problem

¿Cómo creamos y evolucionamos el schema de forma predecible y revisable, y qué hacemos con `dev` (H2)?

---

## 2. Decision Drivers

- Schema predecible y con historial.
- Cambios de schema revisables en el PR.
- Mismo schema en dev, tests y prod (sin divergencias).
- Integración nativa con Spring Boot y poco esfuerzo para el MVP.

---

## 3. Considered Options

Elección de herramienta:

### Option A: `ddl-auto=update` en prod

**Pros:**
- Cero esfuerzo.

**Cons:**
- Schema impredecible.
- No borra columnas ni maneja renombres.
- No deja historial.

### Option B: Flyway

Scripts SQL versionados (`db/migration/V1__...sql`).

**Pros:**
- Integración nativa con Spring Boot.

**Cons:**
- Hay que escribir SQL a mano (ver *Consequences*).

### Option C: Liquibase

**Pros:**
- Más potente.

**Cons:**
- Más verboso (XML/YAML); overkill para el MVP.

Para `dev` (H2), una vez elegido Flyway:

### Option D: Seguir con `create-drop` en dev

**Pros:**
- Cómodo.

**Cons:**
- Las migraciones solo se probarían contra Postgres.
- El schema de dev podría divergir del de prod.

### Option E: Correr también Flyway sobre H2 (`MODE=PostgreSQL`)

**Pros:**
- El schema es el mismo en todos lados.

**Cons:**
- Hay que escribir SQL compatible con las dos bases.

---

## 4. Decision Outcome

**Chosen Option:** Option B (Flyway) + Option E (Flyway también sobre H2), en **todos los perfiles** (dev, tests y prod), con `ddl-auto=validate` en todos: Hibernate solo verifica que las entidades coincidan con el schema que crean las migraciones.

### Rationale

Flyway da scripts versionados e historial sin el peso de Liquibase. Correrlo también sobre H2 garantiza el mismo schema en todos los entornos, a costa de SQL compatible con ambas bases.

- Dependencias: `spring-boot-starter-flyway` (trae `flyway-core`; versión administrada por el BOM de Boot) y `flyway-database-postgresql` (desde Flyway 10, Postgres es un módulo aparte; H2 viene en `flyway-core`).
- Migraciones en `backend/src/main/resources/db/migration/V<n>__<descripcion>.sql`. Una migración ya mergeada no se edita: los cambios van en una nueva.
- SQL compatible con H2 (`MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`) y con Postgres: sin tipos ni funciones exclusivas de Postgres.
- `spring.flyway.clean-disabled=true` en todos los perfiles.
- La primera migración llega con la primera entidad e incluye la tabla de configuración ([ADR 0010](0010-integridad-de-reservas-tiempo-y-configuracion.md)).

---

## 5. Consequences

### Positive Consequences

- El schema es el mismo en dev, tests y prod, y los cambios se revisan en el PR.
- Si una entidad no coincide con el schema, la app no arranca (`validate`), también en dev.

### Negative Consequences

- Cada cambio de modelo trae su script de migración en el mismo PR. Ya no se usa `JPA_DDL_AUTO`.
- Si dos ramas crean la misma versión (`V3`), la que se mergea después la renumera.
- H2 2.4.x supera la versión que Flyway 12.4 tiene verificada (2.3.232): Flyway lo avisa con un WARN al arrancar y por ahora no genera problemas.
- Una base que ya tenga tablas creadas por Hibernate (p. ej. un Postgres local donde se usó `update`) hay que recrearla (`docker compose down -v`) o hacerle baseline.

### Technical Implications

- Si en algún momento hace falta algo exclusivo de Postgres (`jsonb`, índices parciales), habrá que probar las migraciones contra un Postgres real (Testcontainers, ver [ADR 0010](0010-integridad-de-reservas-tiempo-y-configuracion.md)).
- Dependencias nuevas en el backend: `spring-boot-starter-flyway` y `flyway-database-postgresql`.

---

## 6. Implementation and Validation

- [x] Flyway en todos los perfiles con `ddl-auto=validate` y `spring.flyway.clean-disabled=true`.
- [x] Migraciones en `backend/src/main/resources/db/migration/`.
- [x] La primera migración incluye la tabla de configuración (ADR 0010).
- [x] Validación: con `validate`, la app no arranca si entidades y schema difieren (lo cubren los tests, que usan el perfil `dev`).

---

## 7. References

- Issue [#9](https://github.com/Benji-9/SmartElevate/issues/9).
- [ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md).
- `backend/README.md` (sección de base de datos).
