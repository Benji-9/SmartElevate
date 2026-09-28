# 0011. Spring Boot 4.1 y Java 25

- **Estado:** Aceptado
- **Fecha:** 2026-09-28
- **Autores:** @Benji-9

## Contexto

El [ADR 0005](0005-spring-boot-3-5.md) fijó Spring Boot 3.5 + Java 17 porque suponíamos que la cursada lo exigía. No es así: la cátedra no impone stack ni versiones. Además, la línea 3.5 perdió el soporte OSS el **30/06/2026** y ya no recibe parches gratuitos. El backend todavía es un esqueleto, así que migrar ahora es barato.

Versiones verificadas el 2026-09-28 (spring.io, Maven Central, endoflife.date, Docker Hub):

| Componente | Versión | Soporte |
| --- | --- | --- |
| Spring Boot | 4.1.1 | OSS hasta 31/07/2027 (compatible con Java 17–26) |
| springdoc-openapi | 3.1.1 | Línea 3.1, alineada con Boot 4.1 |
| Java (Temurin) | 25 LTS | Hasta 09/2031 |
| Maven / wrapper | 3.9.16 / 3.3.4 | Sin cambios (Maven 4 sigue en RC) |

## Opciones consideradas

1. **Quedarnos en 3.5**: no hay que tocar nada, pero corremos un framework sin parches de seguridad.
2. **Boot 4.0**: su soporte OSS termina el 31/12/2026, antes de que termine el proyecto.
3. **Boot 4.1 + Java 25 LTS**: el soporte más largo disponible. Java 26 no es LTS y su soporte ya venció.

## Decisión

Spring Boot **4.1.1**, springdoc **3.1.1** y Java **25** (Temurin), con los starters modulares de Boot 4 (`spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`, `spring-boot-h2console`). Este ADR reemplaza al 0005.

## Consecuencias

- Jackson 3: los imports pasan de `com.fasterxml.jackson` a `tools.jackson`.
- Las anotaciones de test de MVC viven en `org.springframework.boot.webmvc.test.autoconfigure`.
- Cada tecnología trae su starter de test (p. ej. `spring-boot-starter-data-jpa-test` para `@DataJpaTest`): hay que sumarlo cuando se use.
- Dependabot vuelve a proponer todos los saltos. La 4.2 sale a fines de 2026 y conviene subir antes del 31/07/2027.
