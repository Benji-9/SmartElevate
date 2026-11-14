# Política de seguridad

## Cómo reportar una vulnerabilidad

**No abras un issue público.** Usá el reporte privado de GitHub:

1. Entrá a la pestaña [**Security**](https://github.com/Benji-9/SmartElevate/security) del repo.
2. Elegí **Report a vulnerability** ([guía de GitHub](https://docs.github.com/es/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)).
3. Contanos qué encontraste, cómo reproducirlo y qué impacto tiene.

> **Pendiente del equipo:** confirmar que el reporte privado de vulnerabilidades esté habilitado en *Settings → Code security*. Mientras no lo esté, avisá por mensaje directo a los code owners de [`.github/CODEOWNERS`](.github/CODEOWNERS), sin detalles técnicos en público. Email de contacto: _sin definir_.

Somos un MVP de estudiantes: respondemos en la medida de lo posible, sin plazos garantizados.

## Versiones soportadas

Solo lo que está en `main` (producción). No hay releases con soporte extendido.

## Qué nos interesa

Autenticación y sesión (JWT, refresh en cookie, CSRF), control de roles y prioridad, cupo de turnos, kioscos y QR, y fugas de datos personales. Las decisiones de seguridad están en los ADR [0009](docs/adr/0009-autenticacion-y-prioridad.md), [0015](docs/adr/0015-politica-de-contrasenas-nist-800-63b.md), [0019](docs/adr/0019-refresh-token-en-cookie-httponly.md), [0020](docs/adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md) y [0022](docs/adr/0022-csp-y-headers-de-seguridad-en-vercel.md).

## Si encontrás un secreto en el repo

Avisá por el mismo canal. Hay que **rotarlo** (ver [`docs/infraestructura.md`](docs/infraestructura.md#rotar-la-clave) y el [`.env.example`](.env.example)); borrarlo del historial no alcanza.
