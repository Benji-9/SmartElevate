# Política de seguridad

## Versiones soportadas

Solo lo que está en `main` (producción). No hay versiones antiguas con soporte.

## Cómo reportar una vulnerabilidad

**No abras issues ni PRs públicos con detalles de la vulnerabilidad.** Usá el reporte privado de GitHub:

1. Entrá a [**Report a vulnerability**](https://github.com/Benji-9/SmartElevate/security/advisories/new) (o a la pestaña [Security](https://github.com/Benji-9/SmartElevate/security) del repo).
2. Completá el formulario ([guía de GitHub](https://docs.github.com/es/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)).

Incluí:

- Descripción de la vulnerabilidad.
- Pasos para reproducirla.
- Impacto (qué se puede lograr y a quién afecta).
- Versión o commit afectado.
- Prueba de concepto, **sin datos reales**.

## Qué esperar

Somos un proyecto estudiantil: estos tiempos son **objetivos de mejor esfuerzo**, no un SLA.

- Acuse de recibo: ~7 días.
- Te vamos contando el avance y coordinamos con vos la divulgación una vez corregido.
- No ofrecemos recompensas.

## Alcance

Dentro: el backend (API), el frontend (SPA) y la infraestructura descripta en [`docs/infraestructura.md`](docs/infraestructura.md). Nos interesan sobre todo autenticación y sesión, control de roles y prioridad, cupo de turnos, kioscos y QR, y fugas de datos personales.

Fuera de alcance:

- Denegación de servicio y ataques volumétricos.
- Ingeniería social.
- Hallazgos de proveedores terceros (Render, Neon, Vercel, Brevo): reportalos a ellos.
- Reportes automáticos de escáneres sin impacto demostrado.

## Datos al probar

No uses datos personales reales ni accedas a cuentas ajenas. La app no maneja certificados ni datos de salud ([ADR 0024](docs/adr/0024-movilidad-reducida-la-marca-viene-de-ofalu.md)).

## Controles automáticos activos

- Dependabot alerts y Dependabot security updates.
- Secret scanning con push protection.
- Private vulnerability reporting.
- CodeQL (Java y JavaScript/TypeScript): [`codeql.yml`](.github/workflows/codeql.yml).
- gitleaks sobre el historial completo: [`gitleaks.yml`](.github/workflows/gitleaks.yml).

## Decisiones de seguridad

ADR [0009](docs/adr/0009-autenticacion-y-prioridad.md), [0015](docs/adr/0015-politica-de-contrasenas-nist-800-63b.md), [0019](docs/adr/0019-refresh-token-en-cookie-httponly.md) (incluye la defensa CSRF, antes 0023), [0020](docs/adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md) y [0022](docs/adr/0022-csp-y-headers-de-seguridad-en-vercel.md). Infraestructura: [`docs/infraestructura.md`](docs/infraestructura.md).

## Si encontrás un secreto en el repo

Avisá por el mismo canal. Hay que **rotarlo** (ver [`docs/infraestructura.md`](docs/infraestructura.md#rotar-la-clave) y la [referencia de variables](docs/referencia/variables-de-entorno.md)); borrarlo del historial no alcanza.
