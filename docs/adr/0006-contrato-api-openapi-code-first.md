# 0006. Contrato de la API con OpenAPI (code-first)

- **Estado:** Propuesto
- **Fecha:** 2026-09-28

## Contexto

Backend y frontend avanzan en paralelo y necesitan un contrato compartido (rutas, DTOs, errores). El API contract original no estaba versionado junto al código (B-04, #8), y mantener un documento a mano se desactualiza rápido.

## Opciones consideradas

1. **Documento escrito a mano** (Markdown): fácil de empezar, se desincroniza del código sin que nadie lo note.
2. **Contract-first** (`openapi.yaml` escrito a mano + generación de código): contrato explícito, pero escribir YAML de OpenAPI es lento y el equipo no tiene experiencia.
3. **Code-first con springdoc**: la spec se genera desde los controllers y DTOs; hay que evitar que el front dependa de un backend corriendo.

## Decisión

Code-first, con la spec **versionada** en el repo:

- springdoc genera la spec; Swagger UI (`/swagger-ui.html`) permite explorar y probar los endpoints.
- `OpenApiSpecTest` compara la spec generada con `docs/openapi.json` y falla el build si difieren. Se regenera con `./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true`.
- El frontend genera sus tipos desde `docs/openapi.json` con `openapi-typescript` (`npm run gen:api` → `src/types/openapi.ts`), y el CI del frontend falla si no están al día.
- Para desbloquear al frontend, un endpoint nuevo arranca con controller + DTOs devolviendo datos fijos; la lógica llega después sin cambiar el contrato.

## Consecuencias

- Cada cambio de API se ve en el diff del PR (`docs/openapi.json` y `src/types/openapi.ts`), así se revisa el contrato y no solo el código.
- Un PR que cambia endpoints tiene que regenerar ambos archivos, o el CI falla.
- `openapi-typescript` declara `typescript ^5` como peer; usamos un `override` en `package.json` para que use el TypeScript 6 del proyecto. Revisarlo cuando salga una versión compatible.
