# Architecture Decision Records (ADR)

Cada decisión de arquitectura o de proceso que sea difícil de revertir o que valga la pena explicar a alguien nuevo se registra acá, en un archivo corto.

## Por qué usamos ADRs

Somos 6 personas trabajando en paralelo, con rotación de tareas y entregas parciales para la materia. Las decisiones técnicas se toman en chats o reuniones y después nadie recuerda por qué se eligió algo. Los ADRs viven en `docs/adr/`, versionados junto al código y revisados por PR:

- Quien llega a una parte del código entiende el "por qué" sin preguntar.
- Sirven como evidencia de proceso para la cátedra.
- Costo: ~1 página por decisión importante.

Los bloqueantes y decisiones pendientes viven en [`docs/bloqueantes.md`](../bloqueantes.md).

## Cómo agregar uno

1. Copiar [`template.md`](template.md) (formato [MADR](https://adr.github.io/madr/)) como `NNNN-titulo-corto.md` con el número siguiente y reemplazar `ADR-XXX` por `ADR-NNNN`.
2. Completar todas las secciones; si alguna no aplica, dejarla en blanco en vez de borrarla. Status inicial: **Proposed**.
3. Agregar la fila al [índice](#índice) (al final de la tabla).
4. Abrir PR (`docs(adr): ...`). Al mergearse con acuerdo del equipo pasa a **Accepted**. Un ADR es siempre un cambio **crítico**: sin automerge ([CONTRIBUTING](../../CONTRIBUTING.md#automerge-y-cambios-críticos)).
5. Si una decisión cambia, se escribe un ADR nuevo y el viejo pasa a **Superseded by ADR-NNNN**.

## Status

| Status | Significado |
| --- | --- |
| Proposed | Propuesta, pendiente de revisión |
| Accepted | Aprobada |
| Rejected | Evaluada y no adoptada |
| Deprecated | Ya no se recomienda |
| Superseded | La reemplazó una decisión posterior (indicar `by ADR-NNNN`) |

## Política

- El 2026-11-13 el equipo reescribió todos los ADRs al formato MADR. Desde ahora vuelven a ser registros históricos: no se reescriben, y si una decisión cambia se escribe un ADR nuevo y el viejo pasa a Superseded.
- No se renumera. Cuando varios ADRs tratan lo mismo se pueden fusionar en uno, dejando una lápida en el índice.
- Los archivos se nombran con **cuatro dígitos** (`NNNN-titulo-corto.md`). La plantilla dice tres (`adr-NNN-...`) y su nota sobre qué ADR usan qué formato es genérica: vale lo de esta sección.

## Índice

| # | Título | Estado |
| --- | --- | --- |
| 0001 | Registrar decisiones con ADRs | Eliminado: ver este README |
| [0002](0002-monorepo-backend-frontend.md) | Monorepo con backend y frontend | Accepted |
| [0003](0003-deploy-frontend-vercel-via-github-actions.md) | Deploy del frontend a Vercel vía GitHub Actions | Accepted |
| [0004](0004-migraciones-de-base-de-datos.md) | Migraciones de base de datos con Flyway | Accepted |
| 0005 | Spring Boot 3.5 en lugar de 4.x | Absorbido en 0011 |
| [0006](0006-contrato-api-openapi-code-first.md) | Contrato de la API con OpenAPI (code-first) | Accepted |
| [0007](0007-modelo-de-turnos-y-reservas.md) | Modelo de turnos: la franja es una salida de ascensor | Accepted |
| [0008](0008-check-in-con-qr-rotativo.md) | Check-in con QR rotativo firmado | Accepted |
| [0009](0009-autenticacion-y-prioridad.md) | Autenticación con email institucional y prioridad validada en el servidor | Accepted |
| [0010](0010-integridad-de-reservas-tiempo-y-configuracion.md) | Integridad de reservas, manejo del tiempo y configuración | Accepted |
| [0011](0011-spring-boot-4-y-java-25.md) | Spring Boot 4.1 y Java 25 | Accepted |
| [0012](0012-hosting-del-backend-render-y-neon.md) | Hosting del backend en Render y Postgres en Neon | Accepted |
| [0013](0013-email-con-brevo-y-storage-en-r2.md) | Email con Brevo (y storage en R2, descartado) | Accepted |
| 0014 | Refresh token del frontend en sessionStorage | Fusionado en 0019 |
| [0015](0015-politica-de-contrasenas-nist-800-63b.md) | Adecuación de la política de contraseñas a NIST SP 800-63B | Accepted |
| 0016 | QR firmado con HMAC-SHA256 y kiosco web en el hall | Fusionado en 0008 |
| [0017](0017-donde-viven-los-parametros.md) | Dónde viven los parámetros | Accepted |
| 0018 | Alcance del MVP en las reglas de turnos | Fusionado en 0007 |
| [0019](0019-refresh-token-en-cookie-httponly.md) | Refresh token en cookie HttpOnly con proxy del mismo origen | Accepted |
| [0020](0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md) | Autenticación de kioscos por credencial de dispositivo | Accepted |
| 0021 | Check-in sin ingreso manual del código en el MVP | Fusionado en 0008 |
| [0022](0022-csp-y-headers-de-seguridad-en-vercel.md) | CSP y headers de seguridad en Vercel | Accepted |
| 0023 | Defensa CSRF del refresh por cookie | Fusionado en 0019 |
| [0024](0024-movilidad-reducida-la-marca-viene-de-ofalu.md) | Movilidad reducida: la marca viene de OFALU, SmartElevate no maneja certificados | Accepted |
| [0025](0025-zonas-de-pisos-por-ascensor.md) | Zonas de pisos por ascensor para agrupar destinos antes de reservar | Accepted |
