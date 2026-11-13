# ADR-0011: Spring Boot 4.1 y Java 25

## Metadata

- **Status:** Accepted
- **Date:** 2026-09-28
- **Deciders:** Equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** No figuran en el original
- **Related ADRs:** Absorbe ADR-0005 (Spring Boot 3.5 en lugar de 4.x, reemplazado por este)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0011-spring-boot-4-y-java-25.md`; el ADR 0005 absorbido: `git show d5a37be:docs/adr/0005-spring-boot-3-5.md`)

---

## 1. Context and Problem Statement

### Context

Al armar el esqueleto (septiembre 2026) la última versión estable de Spring Boot era 4.1.x y Spring Initializr ya no generaba proyectos 3.x. El requerimiento que se creía vigente era **Java 17 + Spring Boot 3.x**: el material de la cursada, tutoriales y ejemplos estaban en 3.x. Con eso se había fijado (ex ADR 0005) Spring Boot **3.5.16** (última 3.x en Maven Central), springdoc-openapi **2.9.1** (línea 2.x, compilada contra Boot 3.5.16; la 3.x es para Boot 4) y Maven Wrapper 3.3.4 con Maven 3.9.16.

Esa premisa era falsa: la cátedra no impone stack ni versiones. Además, la línea 3.5 perdió el soporte OSS el **30/06/2026** y ya no recibe parches gratuitos. El backend todavía es un esqueleto, así que migrar ahora es barato.

Versiones verificadas el 2026-09-28 (spring.io, Maven Central, endoflife.date, Docker Hub):

| Componente | Versión | Soporte |
| --- | --- | --- |
| Spring Boot | 4.1.1 | OSS hasta 31/07/2027 (compatible con Java 17–26) |
| springdoc-openapi | 3.1.1 | Línea 3.1, alineada con Boot 4.1 |
| Java (Temurin) | 25 LTS | Hasta 09/2031 |
| Maven / wrapper | 3.9.16 / 3.3.4 | Sin cambios (Maven 4 sigue en RC) |

### Problem

¿Qué versión de Spring Boot y de Java usamos, ahora que sabemos que la cursada no exige Boot 3.x / Java 17?

---

## 2. Decision Drivers

- Soporte OSS (parches de seguridad) durante todo el proyecto.
- Costo de migrar: hoy es mínimo porque el backend es un esqueleto.
- Soporte más largo disponible.
- Que no haya una restricción real de la cátedra.

---

## 3. Considered Options

### Option A: Spring Boot 3.5 + Java 17 (decisión previa, ex ADR 0005)

Quedarnos en 3.5. Era la elección original por el requerimiento supuesto de la cursada.

**Pros:**
- No hay que tocar nada; el material de la cursada y los tutoriales están en 3.x.

**Cons:**
- Corremos un framework sin parches de seguridad (soporte OSS terminado el 30/06/2026).
- Dependabot propone saltos de major (Boot 4, springdoc 3) que no se podían mergear sin un ADR nuevo.

### Option B: Boot 4.0

**Cons:**
- Su soporte OSS termina el 31/12/2026, antes de que termine el proyecto.

### Option C: Boot 4.1 + Java 25 LTS

**Pros:**
- El soporte más largo disponible. Java 26 no es LTS y su soporte ya venció.

---

## 4. Decision Outcome

**Chosen Option:** C. Spring Boot **4.1.1**, springdoc **3.1.1** y Java **25** (Temurin), con los starters modulares de Boot 4 (`spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`, `spring-boot-h2console`). Este ADR reemplaza a la decisión previa de Boot 3.5 (ex ADR 0005).

### Rationale

La cátedra no impone stack, la línea 3.5 ya no tiene parches y el backend es un esqueleto: migrar ahora es barato y da el soporte OSS más largo (hasta 31/07/2027 para Boot, 09/2031 para Java).

---

## 5. Consequences

### Positive Consequences

- Framework con parches de seguridad y Java LTS hasta 09/2031.

### Negative Consequences

- Jackson 3: los imports pasan de `com.fasterxml.jackson` a `tools.jackson`.
- Cada tecnología trae su starter de test (p. ej. `spring-boot-starter-data-jpa-test` para `@DataJpaTest`): hay que sumarlo cuando se use.
- Dependabot vuelve a proponer todos los saltos. La 4.2 sale a fines de 2026 y conviene subir antes del 31/07/2027.

### Technical Implications

- Las anotaciones de test de MVC viven en `org.springframework.boot.webmvc.test.autoconfigure`.

---

## 6. Implementation and Validation

- [x] Backend en Spring Boot 4.1.1, springdoc 3.1.1, Java 25 (Temurin) y starters modulares.
- [ ] Planificar la subida a Boot 4.2 antes del 31/07/2027 (fin del soporte OSS de 4.1).

---

## 7. References

- Versiones verificadas el 2026-09-28 en spring.io, Maven Central, endoflife.date y Docker Hub.
- Decisión previa absorbida (ex ADR 0005): Spring Boot 3.5.16, springdoc 2.9.1, Maven Wrapper 3.3.4 / Maven 3.9.16.
