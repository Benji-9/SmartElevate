# 0005. Spring Boot 3.5 en lugar de 4.x

- **Estado:** Aceptado
- **Fecha:** 2026-09-28

## Contexto

Al armar el esqueleto (septiembre 2026) la última versión estable de Spring Boot es 4.1.x y Spring Initializr ya no genera proyectos 3.x. El requerimiento del equipo es **Java 17 + Spring Boot 3.x**: el material de la cursada, tutoriales y ejemplos que usamos están en 3.x.

## Decisión

- Spring Boot **3.5.16** (última 3.x publicada en Maven Central al momento).
- springdoc-openapi **2.9.1** (línea 2.x, compilada contra Boot 3.5.16; la 3.x es para Boot 4).
- Maven Wrapper 3.3.4 con Maven 3.9.16.

## Consecuencias

- Hay que revisar el fin de soporte OSS de la línea 3.5 y planificar la migración a Boot 4 si el proyecto sigue después del MVP.
- Dependabot va a proponer saltos de major (Boot 4, springdoc 3): **no mergearlos** sin un ADR nuevo; los minor/patch vienen agrupados.
