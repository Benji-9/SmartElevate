# 0017. Dónde viven los parámetros

- **Estado:** Aceptado
- **Fecha:** 2026-10-14
- **Issues:** [#218](https://github.com/Benji-9/SmartElevate/issues/218). Precisa el punto 4 del [ADR 0010](0010-integridad-de-reservas-tiempo-y-configuracion.md).
- **Autores:** @Benji-9

## Contexto

El [ADR 0010](0010-integridad-de-reservas-tiempo-y-configuracion.md) puso los parámetros de negocio en la tabla `app_config`, pero no dijo nada de los técnicos y de seguridad. Quedaron mezclados: los TTL de JWT, verificación y reset viven en `application.yml`, y los límites de intentos de login ([#211](https://github.com/Benji-9/SmartElevate/issues/211), [ADR 0015](0015-politica-de-contrasenas-nist-800-63b.md)) se agregaron a `app_config` en `V13`.

## Opciones consideradas

1. **Todo en `app_config`**: un solo lugar, pero un `UPDATE` en la base podría debilitar la seguridad (TTL largos, sin tope de intentos) sin pasar por un PR, y los secretos no pueden ir ahí.
2. **Todo en `application.yml` y variables de entorno**: versionado y revisado, pero calibrar el cupo o una ventana en el piloto pediría redeployar.
3. **Por tipo de parámetro**: secretos en variables de entorno, técnicos y de seguridad en `application.yml` con override por variable, y reglas de negocio en `app_config`.

## Decisión

Opción **3**:

| Tipo | Dónde | Ejemplos |
| --- | --- | --- |
| **Secretos** | Solo variables de entorno, nunca en el repo | `JWT_SECRET`, `SPRING_MAIL_PASSWORD` (SMTP), clave HMAC del QR ([ADR 0016](0016-qr-firmado-con-hmac-y-kiosco-web.md)) |
| **Técnicos y de seguridad** | `application.yml`, con override por variable de entorno | TTL de JWT (`JWT_ACCESS_TTL`, `JWT_REFRESH_TTL`), vencimiento y espera de los links de verificación y de reset, límites de intentos de login |
| **Reglas de negocio** ajustables sin redeploy | `app_config`, nunca hardcodeadas | Cupo por salida, ventanas de reserva y check-in, plazo de cancelación sin falta (10 min), lugares prioritarios, cierre del cuatrimestre |

## Consecuencias

- Un cambio de seguridad pasa por un PR o por una variable del servicio, no por un `UPDATE` suelto.
- QA y el equipo siguen calibrando las reglas de negocio en la base, reiniciando la app (ADR 0010).
- Los tres límites de intentos de login (`login_failure_window`, `login_max_failures_per_account`, `login_max_failures_per_ip`) se movieron de `app_config` a `application.yml` (`app.login-throttle.*`) en [#225](https://github.com/Benji-9/SmartElevate/issues/225): `V14` borró las filas que había agregado `V13`.
