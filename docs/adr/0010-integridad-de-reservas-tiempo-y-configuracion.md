# 0010. Integridad de reservas, manejo del tiempo y configuración

- **Estado:** Aceptado
- **Fecha:** 2026-09-28
- **Issue:** [#7](https://github.com/Benji-9/SmartElevate/issues/7)

## Contexto

Las reglas de turnos dependen de un cupo compartido (10 lugares), de horarios locales y de muchos parámetros (tiempos, ventanas, conexiones). El servidor probablemente corra en UTC: Render, en evaluación en [#5](https://github.com/Benji-9/SmartElevate/issues/5), corre en UTC. QA necesita simular horas pico.

## Decisión

1. **Cupo atómico**: la reserva del último lugar se protege con un **lock pesimista** sobre la salida (`SELECT … FOR UPDATE`) o con un **constraint único** (salida + número de lugar), para que dos personas no tomen el mismo lugar. Se cubre con un test de concurrencia.
2. **Tiempo**: los instantes se guardan en **UTC** (`Instant`). Las franjas y el horario de operación se calculan en **America/Argentina/Buenos_Aires**. Nunca usar la zona horaria del servidor.
3. **Reloj inyectable**: se inyecta un bean `java.time.Clock` y ningún service llama a `Instant.now()` ni a `LocalDateTime.now()` directo. Así los tests y QA pueden fijar la hora (por ejemplo, "martes 12:55").
4. **Configuración en tabla**: tiempos, capacidad, ventanas, umbrales, ascensores y conexiones se guardan en una **tabla de configuración con validación** al cargar (rangos, pisos dentro del edificio, capacidad > 0), no hardcodeados. Valores iniciales en [reglas](../reglas/README.md#parámetros-configurables).

## Consecuencias

- La tabla de configuración va en la primera migración de Flyway ([#9](https://github.com/Benji-9/SmartElevate/issues/9), [ADR 0004](0004-migraciones-de-base-de-datos.md)).
- Tests de services con un `Clock.fixed(...)`.
- Los tests de concurrencia requieren una base real: H2 no reproduce fielmente los locks de Postgres. Evaluar Testcontainers para ese caso.
