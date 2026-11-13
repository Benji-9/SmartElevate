# ADR-0017: Dónde viven los parámetros

## Metadata

- **Status:** Accepted
- **Date:** 2026-10-14
- **Deciders:** Equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** [#218](https://github.com/Benji-9/SmartElevate/issues/218), [#211](https://github.com/Benji-9/SmartElevate/issues/211), [#225](https://github.com/Benji-9/SmartElevate/issues/225)
- **Related ADRs:** [ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md) (precisa su punto 4), [ADR-0015](0015-politica-de-contrasenas-nist-800-63b.md), [ADR-0008](0008-check-in-con-qr-rotativo.md)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0017-donde-viven-los-parametros.md`)

---

## 1. Context and Problem Statement

### Context

El [ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md) puso los parámetros de negocio en la tabla `app_config`, pero no dijo nada de los técnicos y de seguridad. Quedaron mezclados: los TTL de JWT, verificación y reset viven en `application.yml`, y los límites de intentos de login ([#211](https://github.com/Benji-9/SmartElevate/issues/211), [ADR-0015](0015-politica-de-contrasenas-nist-800-63b.md)) se agregaron a `app_config` en `V13`.

### Problem

¿Dónde vive cada tipo de parámetro (secretos, técnicos y de seguridad, reglas de negocio)?

---

## 2. Decision Drivers

- Que un cambio de seguridad no se pueda hacer con un `UPDATE` suelto en la base.
- Que los secretos nunca estén en el repo ni en la base.
- Poder calibrar reglas de negocio (cupo, ventanas) en el piloto sin redeployar.

---

## 3. Considered Options

### Option A: Todo en `app_config`

**Pros:**
- Un solo lugar.

**Cons:**
- Un `UPDATE` en la base podría debilitar la seguridad (TTL largos, sin tope de intentos) sin pasar por un PR.
- Los secretos no pueden ir ahí.

### Option B: Todo en `application.yml` y variables de entorno

**Pros:**
- Versionado y revisado.

**Cons:**
- Calibrar el cupo o una ventana en el piloto pediría redeployar.

### Option C: Por tipo de parámetro

Secretos en variables de entorno, técnicos y de seguridad en `application.yml` con override por variable, y reglas de negocio en `app_config`.

---

## 4. Decision Outcome

**Chosen Option:** C.

### Rationale

Cada tipo va donde corresponde a su riesgo y a su ritmo de cambio:

| Tipo | Dónde | Ejemplos |
| --- | --- | --- |
| **Secretos** | Solo variables de entorno, nunca en el repo | `JWT_SECRET`, `SPRING_MAIL_PASSWORD` (SMTP), clave HMAC del QR ([ADR-0008](0008-check-in-con-qr-rotativo.md)) |
| **Técnicos y de seguridad** | `application.yml`, con override por variable de entorno | TTL de JWT (`JWT_ACCESS_TTL`, `JWT_REFRESH_TTL`), vencimiento y espera de los links de verificación y de reset, límites de intentos de login |
| **Reglas de negocio** ajustables sin redeploy | `app_config`, nunca hardcodeadas | Cupo por salida, ventanas de reserva y check-in, plazo de cancelación sin falta (10 min), lugares prioritarios, cierre del cuatrimestre |

---

## 5. Consequences

### Positive Consequences

- Un cambio de seguridad pasa por un PR o por una variable del servicio, no por un `UPDATE` suelto.
- QA y el equipo siguen calibrando las reglas de negocio en la base, reiniciando la app ([ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md)).

### Negative Consequences

- Los parámetros quedan repartidos en tres lugares; hay que saber a qué tipo pertenece cada uno.

### Technical Implications

- Los tres límites de intentos de login (`login_failure_window`, `login_max_failures_per_account`, `login_max_failures_per_ip`) se movieron de `app_config` a `application.yml` (`app.login-throttle.*`) en [#225](https://github.com/Benji-9/SmartElevate/issues/225): `V14` borró las filas que había agregado `V13`.

---

## 6. Implementation and Validation

- [x] Límites de intentos de login movidos a `application.yml` (`app.login-throttle.*`) en [#225](https://github.com/Benji-9/SmartElevate/issues/225); migración `V14` borra las filas de `V13`.
- [x] TTL de JWT, verificación y reset en `application.yml` con override por variable de entorno.
- [ ] Revisar en cada PR que los secretos no entren al repo y que ninguna regla de negocio quede hardcodeada.

---

## 7. References

- [ADR-0010](0010-integridad-de-reservas-tiempo-y-configuracion.md), [ADR-0015](0015-politica-de-contrasenas-nist-800-63b.md), [ADR-0008](0008-check-in-con-qr-rotativo.md).
- Issues: [#218](https://github.com/Benji-9/SmartElevate/issues/218), [#211](https://github.com/Benji-9/SmartElevate/issues/211), [#225](https://github.com/Benji-9/SmartElevate/issues/225).
