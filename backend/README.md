# Backend

API REST de SmartElevate: Java 25 + Spring Boot 4.1 (Maven, Spring Data JPA, Flyway, H2 en dev y PostgreSQL en prod, springdoc-openapi). Decisiones: [ADR 0011](../docs/adr/0011-spring-boot-4-y-java-25.md) (stack) y [ADR 0012](../docs/adr/0012-hosting-del-backend-render-y-neon.md) (hosting).

## Requisitos

- **JDK 25** (Temurin recomendado; el proyecto compila con `--release 25`).
- **Docker** (opcional): Postgres local, imagen del backend y tests con Testcontainers.

No hace falta Maven: se usa `./mvnw` (en Windows, `mvnw.cmd`).

## Comandos

Desde `backend/`:

```bash
./mvnw spring-boot:run                  # perfil dev (H2 en memoria), http://localhost:8080
./mvnw verify                           # build + tests (lo que corre el CI)
./mvnw test -Dtest=PingControllerTest   # un test puntual
```

Al levantar en dev:

- API: http://localhost:8080/api/ping → `{"status":"ok"}`
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Consola H2 (solo dev): http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:smartelevate`, usuario `sa`, sin password)

### Con Postgres (perfil `prod`)

```bash
docker compose up -d                       # desde la raíz del repo
cd backend
export SPRING_PROFILES_ACTIVE=prod
export DATABASE_URL=jdbc:postgresql://localhost:5432/smartelevate
export DATABASE_USER=smartelevate
export DATABASE_PASSWORD=smartelevate
./mvnw spring-boot:run                     # Flyway crea el schema al arrancar
```

El perfil `prod` exige más variables (JWT, CORS, mail, QR): sin ellas la app no arranca. Están en la sección de `prod` del [`.env.example`](../.env.example) (`cp ../.env.example ../.env` y completá; el backend lo carga solo); generá los secretos, no copies ejemplos.

## Variables de entorno

Todas las variables, sus defaults y la checklist de `prod`: [`.env.example`](../.env.example) en la raíz. La fuente de verdad de los defaults son los `src/main/resources/application*.yml`. En local, `application.yml` importa `../.env` si existe (el mismo que lee docker compose); las variables reales del entorno lo pisan, en Docker/Render no existe y se ignora, y `./mvnw test` no lo lee. `APP_ENV_FILE` apunta a otro archivo. Los secretos van solo en variables de entorno, nunca en el repo ([ADR 0017](../docs/adr/0017-donde-viven-los-parametros.md)).

## Estructura

```
src/main/java/com/smartelevate/
├── common/      config, error, mail, ping, security, time, validation
├── user/        ┐
├── elevator/    │ un paquete por feature, con capas:
├── turn/        │ controller / service / repository / dto / model
└── priority/    ┘
src/main/resources/
├── application.yml, application-dev.yml, application-prod.yml
├── db/migration/    migraciones Flyway
└── security/
Dockerfile
```

## Convenciones

- Controllers bajo `/api/...`, delgados: validan (`@Valid`), delegan en el service y devuelven DTOs (records). **Nunca exponer entidades JPA.**
- La lógica de negocio (cupo de 10, prioridades) vive en los services y se testea con tests unitarios.
- Errores: lanzar excepciones y dejar que `common/error/GlobalExceptionHandler` arme el `ApiError`.
- Hora: `common/time/CampusTime` o el bean `Clock`; nunca `Instant.now()` directo en services ([ADR 0010](../docs/adr/0010-integridad-de-reservas-tiempo-y-configuracion.md)).
- Lombok disponible: `record` para DTOs y `@RequiredArgsConstructor` para inyección.
- Antes de tocar `turn`, `elevator`, `user` o `priority`, leé [`docs/reglas/`](../docs/reglas/README.md).
- Cómo agregar un endpoint público, protegido o por rol: [Autenticación y endpoints protegidos](../docs/guias/autenticacion-y-endpoints.md).

Las reglas completas para agentes y personas están en [`AGENTS.md`](../AGENTS.md#arquitectura).

## Migraciones de la base (Flyway)

El schema lo crean los scripts de `src/main/resources/db/migration/`, igual en dev (H2), en los tests y en prod (Postgres). Hibernate solo valida que las entidades coincidan (`ddl-auto: validate`). Decisión en el [ADR 0004](../docs/adr/0004-migraciones-de-base-de-datos.md).

El esquema resultante está en el [diagrama entidad-relación](../docs/referencia/der.md); si una migración cambia tablas o constraints, actualizalo en el mismo PR.

- Un script por cambio, con nombre `V<n>__<descripcion>.sql` (p. ej. `V2__agregar_ascensor_a_reservas.sql`), en el mismo PR que el cambio de entidades.
- **Una migración ya mergeada no se edita**: Flyway detecta el cambio y la app no arranca. Se corrige con una migración nueva.
- SQL compatible con H2 (`MODE=PostgreSQL`) y con Postgres: sin tipos ni funciones exclusivas de Postgres.
- Si ya tenías un Postgres local con tablas creadas por Hibernate, recrealo con `docker compose down -v` y volvé a levantarlo.

## Contrato OpenAPI

La documentación de los endpoints se genera desde el código (springdoc) y queda versionada en [`docs/openapi.json`](../docs/openapi.json) ([ADR 0006](../docs/adr/0006-contrato-api-openapi-code-first.md)). `OpenApiSpecTest` falla si está desactualizada.

- **Swagger UI:** http://localhost:8080/swagger-ui.html (con "Try it out").
- **Spec (JSON):** http://localhost:8080/v3/api-docs
- **Tipos TypeScript:** `frontend/src/types/openapi.ts`, generados desde la spec.

Cuando agregás o cambiás un endpoint o un DTO:

```bash
cd backend
./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true   # regenera docs/openapi.json
cd ../frontend
npm run gen:api                                            # regenera src/types/openapi.ts
```

Commiteá los dos archivos en el mismo PR. Si te olvidás, falla el CI (`backend-verify` o `frontend-verify`) con el comando a correr.

Documentá con anotaciones: `@Tag` y `@Operation` en el controller, `@Schema` en los DTOs (`requiredMode = REQUIRED` para campos obligatorios) y Bean Validation (`@NotNull`, `@Size`), que también aparece en la spec.

## Tests

- `./mvnw verify` corre todo. Los tests usan el perfil `dev` (H2).
- Boot 4 usa starters modulares: para testear una tecnología sumá su starter `*-test` (p. ej. `spring-boot-starter-data-jpa-test`). Jackson 3 (`tools.jackson`).
- `@WebMvcTest`: anotar con `@WithAppSecurity` (carga las reglas reales de seguridad) y usar `jwt()` de `spring-security-test`.
- Tiempo: `Clock.fixed(...)`.
- **Contra Postgres real.** Casi todos los tests usan H2, pero los que dependen de Postgres (migraciones, locks del cupo) levantan un `postgres:18-alpine` en Docker con [Testcontainers](https://java.testcontainers.org/). Necesitan Docker corriendo; **sin Docker se saltean** (salen como *skipped*) y `./mvnw verify` sigue pasando. En CI siempre corren. Para un test nuevo: `@Import(PostgresTestcontainersConfig.class)` + `@Testcontainers(disabledWithoutDocker = true)` (ver `FlywayPostgresTest`).

## Imagen Docker

```bash
cd backend
docker build -t smartelevate-api .
docker run --rm -p 8080:8080 -e DATABASE_URL=... -e DATABASE_USER=... -e DATABASE_PASSWORD=... smartelevate-api
```

La imagen corre con perfil `prod`, usuario no root y respeta la variable `PORT`. Con `prod` hacen falta además las variables obligatorias de la [checklist de `prod`](../.env.example). Operación en Render: [`docs/infraestructura.md`](../docs/infraestructura.md#backend-en-render).
