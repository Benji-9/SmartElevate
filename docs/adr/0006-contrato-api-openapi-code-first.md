# ADR-0006: Contrato de la API con OpenAPI (code-first)

## Metadata

- **Status:** Accepted
- **Date:** 2026-09-28
- **Deciders:** Equipo SmartElevate
- **Owner:** No figura en el ADR original
- **Related Issues:** B-04 ([#8](https://github.com/Benji-9/SmartElevate/issues/8))
- **Related ADRs:** No aplica
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0006-contrato-api-openapi-code-first.md`)

---

## 1. Context and Problem Statement

### Context

Backend y frontend avanzan en paralelo y necesitan un contrato compartido (rutas, DTOs, errores). El API contract original no estaba versionado junto al código (B-04, #8), y mantener un documento a mano se desactualiza rápido.

### Problem

¿Cómo definimos y mantenemos un contrato de API compartido que no se desincronice del código?

---

## 2. Decision Drivers

- Trabajo en paralelo de backend y frontend.
- El contrato no debe desactualizarse sin que nadie lo note.
- El equipo no tiene experiencia escribiendo OpenAPI a mano.
- El frontend no debe depender de un backend corriendo.

---

## 3. Considered Options

### Option A: Documento escrito a mano (Markdown)

**Pros:**
- Fácil de empezar.

**Cons:**
- Se desincroniza del código sin que nadie lo note.

### Option B: Contract-first (`openapi.yaml` a mano + generación de código)

**Pros:**
- Contrato explícito.

**Cons:**
- Escribir YAML de OpenAPI es lento y el equipo no tiene experiencia.

### Option C: Code-first con springdoc

**Pros:**
- La spec se genera desde los controllers y DTOs.

**Cons:**
- Hay que evitar que el front dependa de un backend corriendo.

---

## 4. Decision Outcome

**Chosen Option:** Option C, code-first con springdoc, con la spec **versionada** en el repo.

### Rationale

La spec sale del código, así no se desactualiza; versionarla en el repo (`docs/openapi.json`) resuelve la dependencia del front con un backend corriendo.

- springdoc genera la spec; Swagger UI (`/swagger-ui.html`) permite explorar y probar los endpoints.
- `OpenApiSpecTest` compara la spec generada con `docs/openapi.json` y falla el build si difieren. Se regenera con `./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true`.
- El frontend genera sus tipos desde `docs/openapi.json` con `openapi-typescript` (`npm run gen:api` → `src/types/openapi.ts`), y el CI del frontend falla si no están al día.
- Para desbloquear al frontend, un endpoint nuevo arranca con controller + DTOs devolviendo datos fijos; la lógica llega después sin cambiar el contrato.

---

## 5. Consequences

### Positive Consequences

- Cada cambio de API se ve en el diff del PR (`docs/openapi.json` y `src/types/openapi.ts`), así se revisa el contrato y no solo el código.

### Negative Consequences

- Un PR que cambia endpoints tiene que regenerar ambos archivos, o el CI falla.
- `openapi-typescript` declara `typescript ^5` como peer; usamos un `override` en `package.json` para que use el TypeScript 6 del proyecto. Revisarlo cuando salga una versión compatible.

### Technical Implications

- Backend: springdoc, `OpenApiSpecTest` y `docs/openapi.json`.
- Frontend: `openapi-typescript`, script `gen:api` y `src/types/openapi.ts` (generado, no se edita).
- CI del frontend verifica que los tipos estén al día.

---

## 6. Implementation and Validation

- [x] springdoc + Swagger UI en `/swagger-ui.html`.
- [x] `OpenApiSpecTest` falla si `docs/openapi.json` está desactualizado.
- [x] `npm run gen:api` genera `src/types/openapi.ts`; el CI del frontend lo verifica.
- [ ] Revisar el `override` de TypeScript cuando `openapi-typescript` soporte TypeScript 6.

---

## 7. References

- B-04 en `docs/bloqueantes.md`, issue [#8](https://github.com/Benji-9/SmartElevate/issues/8).
- `docs/openapi.json`.
