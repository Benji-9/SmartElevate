# ADR-0010: Integridad de reservas, manejo del tiempo y configuración

## Metadata

- **Status:** Accepted
- **Date:** 2026-09-28
- **Deciders:** Equipo SmartElevate
- **Owner:** No figura en el original
- **Related Issues:** [#7](https://github.com/Benji-9/SmartElevate/issues/7), [#5](https://github.com/Benji-9/SmartElevate/issues/5), [#9](https://github.com/Benji-9/SmartElevate/issues/9)
- **Related ADRs:** [ADR-0004](0004-migraciones-de-base-de-datos.md), [ADR-0007](0007-modelo-de-turnos-y-reservas.md), [ADR-0017](0017-donde-viven-los-parametros.md) (precisa el punto 4)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0010-integridad-de-reservas-tiempo-y-configuracion.md`)

---

## 1. Context and Problem Statement

### Context

Las reglas de turnos dependen de un cupo compartido (10 lugares), de horarios locales y de muchos parámetros (tiempos, ventanas, conexiones). El servidor probablemente corra en UTC: Render, en evaluación en [#5](https://github.com/Benji-9/SmartElevate/issues/5), corre en UTC. QA necesita simular horas pico.

### Problem

¿Cómo garantizamos que dos personas no tomen el mismo lugar, cómo manejamos el tiempo de forma consistente y testeable, y dónde guardamos los parámetros?

---

## 2. Decision Drivers

- Integridad del cupo bajo concurrencia.
- Independencia de la zona horaria del servidor (UTC).
- Poder fijar la hora en tests y en QA (simular horas pico).
- Parámetros ajustables y validados, no hardcodeados.

---

## 3. Considered Options

El ADR original no registró alternativas descartadas; las únicas opciones que menciona son dos mecanismos equivalentes para el cupo atómico:

### Option A: Lock pesimista sobre la salida (`SELECT … FOR UPDATE`)

### Option B: Constraint único (salida + número de lugar)

El resto de los puntos (UTC, `Clock` inyectable, tabla de configuración) se decidieron sin opciones alternativas documentadas.

---

## 4. Decision Outcome

**Chosen Option:** cupo atómico por lock pesimista o constraint único, tiempo en UTC con franjas en Buenos Aires, `Clock` inyectable y configuración en tabla.

### Rationale

1. **Cupo atómico**: la reserva del último lugar se protege con un **lock pesimista** sobre la salida (`SELECT … FOR UPDATE`) o con un **constraint único** (salida + número de lugar), para que dos personas no tomen el mismo lugar. Se cubre con un test de concurrencia.
2. **Tiempo**: los instantes se guardan en **UTC** (`Instant`). Las franjas y el horario de operación se calculan en **America/Argentina/Buenos_Aires**. Nunca usar la zona horaria del servidor.
3. **Reloj inyectable**: se inyecta un bean `java.time.Clock` y ningún service llama a `Instant.now()` ni a `LocalDateTime.now()` directo. Así los tests y QA pueden fijar la hora (por ejemplo, "martes 12:55").
4. **Configuración en tabla**: tiempos, capacidad, ventanas, umbrales, ascensores y conexiones se guardan en una **tabla de configuración con validación** al cargar (rangos, pisos dentro del edificio, capacidad > 0), no hardcodeados. Valores iniciales en [reglas](../reglas/README.md#parámetros-configurables). El [ADR-0017](0017-donde-viven-los-parametros.md) precisa qué parámetros van en la tabla y cuáles no.

---

## 5. Consequences

### Positive Consequences

- Sin sobreventa del cupo y comportamiento temporal reproducible en tests y QA.

### Negative Consequences

- Los tests de concurrencia requieren una base real: H2 no reproduce fielmente los locks de Postgres.

### Technical Implications

- La tabla de configuración va en la primera migración de Flyway ([#9](https://github.com/Benji-9/SmartElevate/issues/9), [ADR-0004](0004-migraciones-de-base-de-datos.md)).
- Tests de services con un `Clock.fixed(...)`.
- Evaluar Testcontainers para los tests de concurrencia.

---

## 6. Implementation and Validation

- [ ] Test de concurrencia del cupo (evaluar Testcontainers, por la diferencia de locks entre H2 y Postgres).
- [ ] Tests de services con `Clock.fixed(...)`; ningún service usa `Instant.now()` ni `LocalDateTime.now()` directo.
- [ ] Tabla de configuración en la primera migración de Flyway, con validación al cargar.

---

## 7. References

- [ADR-0004](0004-migraciones-de-base-de-datos.md), [ADR-0007](0007-modelo-de-turnos-y-reservas.md), [ADR-0017](0017-donde-viven-los-parametros.md).
- [Parámetros configurables](../reglas/README.md#parámetros-configurables).
- Issues: [#7](https://github.com/Benji-9/SmartElevate/issues/7), [#5](https://github.com/Benji-9/SmartElevate/issues/5), [#9](https://github.com/Benji-9/SmartElevate/issues/9).
