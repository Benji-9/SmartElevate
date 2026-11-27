# ADR-0022: CSP y headers de seguridad en Vercel

## Metadata

- **Status:** Accepted
- **Date:** 2026-10-15
- **Deciders:** Equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** [#223](https://github.com/Benji-9/SmartElevate/issues/223); relacionado con [#204](https://github.com/Benji-9/SmartElevate/issues/204)
- **Related ADRs:** [ADR-0003](0003-deploy-frontend-vercel-via-github-actions.md), [ADR-0019](0019-refresh-token-en-cookie-httponly.md) (que absorbió al ex ADR-0014)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0022-csp-y-headers-de-seguridad-en-vercel.md`)

---

## 1. Context and Problem Statement

### Context

El ex ADR-0014 (refresh token en `sessionStorage`, hoy absorbido en el [ADR-0019](0019-refresh-token-en-cookie-httponly.md)) dejó la CSP como mitigación pendiente ante un XSS que lea el refresh token de `sessionStorage`.

Auditoría del front (`npm run build`):

- El HTML no tiene scripts ni `<style>` inline.
- No hay orígenes externos: Inter sale de `@fontsource-variable/inter`, empaquetada en `dist/assets`.
- El QR del kiosco es un `<svg>` inline.
- Los `smartelevate-logo*.svg` (~19 KB) salen como `/assets/*.svg` del mismo origen y solo `uade-logo.svg` (1,9 KB) queda inlineado como `data:image/svg+xml`; ambos pasan con `img-src 'self' data:`.
- El navegador solo habla con el mismo origen: `/api/*` lo reenvía Vercel al backend con un rewrite.
- React usa atributos `style=` (anchos de barras, `--value`).

### Problem

¿Qué CSP y headers de seguridad servimos desde Vercel para limitar el impacto de un XSS sin romper el front?

---

## 2. Decision Drivers

- Limitar el impacto de un XSS (mitigación pendiente desde el ex ADR-0014).
- El front no usa scripts ni estilos inline ni orígenes externos, así que se puede ser estricto.
- El check-in escanea con la cámara.
- No hay endpoint de reporte para CSP.
- No tocar el reemplazo de `${BACKEND_URL}` que hace el workflow de deploy.

---

## 3. Considered Options

### Option A: CSP estricta aplicada (`Content-Security-Policy`)

**Pros:**
- Bloquea de verdad.

**Cons:**
- Una violación no detectada rompe una pantalla en producción.

### Option B: `Content-Security-Policy-Report-Only`

**Cons:**
- No hay endpoint de reporte, así que no daría información.

### Option C: Permitir `unsafe-inline` en scripts y `<style>`

**Cons:**
- Anula buena parte de la protección contra XSS. Los `style=` de React son inevitables, pero se pueden permitir solo como atributo.

### Option D: Sumar `${BACKEND_URL}` a `connect-src`

**Cons:**
- Innecesario: el navegador nunca llama al backend directo.

---

## 4. Decision Outcome

**Chosen Option:** Option A, CSP real sin `unsafe-inline` en scripts ni hojas de estilo, en [`frontend/vercel.json`](../../frontend/vercel.json), para todas las rutas.

### Rationale

Headers:

- `Content-Security-Policy`: `default-src 'self'; script-src 'self'; style-src 'self'; style-src-attr 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; media-src 'self' blob:; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`.
- `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`.
- `Permissions-Policy: camera=(self), microphone=(), geolocation=(), payment=(), usb=()` (el check-in escanea con la cámara).
- HSTS: no se agrega, Vercel ya lo manda en los dominios `*.vercel.app` y en los propios.

Puntos a justificar:

- **Sin `unsafe-inline` en scripts ni en `<style>`.** Los `style=` de React son inevitables, así que se permiten solo como atributo con `style-src-attr 'unsafe-inline'`; las hojas de estilo siguen siendo `'self'`. Un atributo `style` no ejecuta código: el riesgo es de exfiltración por CSS, bajo.
- **`connect-src 'self'`** sin `${BACKEND_URL}`: el navegador nunca llama al backend directo, así que el placeholder no se usa en la CSP y el reemplazo del workflow no cambia (solo toca el rewrite de `/api`).
- Se aplica la CSP real y no `Report-Only`: no hay endpoint de reporte.

---

## 5. Consequences

### Positive Consequences

- Un XSS tiene mucho menos margen: sin scripts inline ni orígenes externos.

### Negative Consequences

- Cualquier recurso externo nuevo (fuentes de Google, analytics, un CDN) se va a bloquear hasta sumarlo a la CSP en este archivo.
- Riesgo: una violación no detectada rompe una pantalla en producción. Se mitiga con el checklist de verificación en el preview ([infraestructura](../infraestructura.md#headers-de-seguridad-csp)).

### Technical Implications

- Si [#204](https://github.com/Benji-9/SmartElevate/issues/204) mantiene el proxy del mismo origen (ver [ADR-0019](0019-refresh-token-en-cookie-httponly.md)), `connect-src` sigue en `'self'`. Solo habría que sumar el origen del backend si se dejara de usar el rewrite, y ahí el placeholder `${BACKEND_URL}` tendría que reemplazarse también en la CSP.

---

## 6. Implementation and Validation

- [x] Headers declarados en [`frontend/vercel.json`](../../frontend/vercel.json) para todas las rutas.
- [x] Auditoría del build del front para confirmar que no hay scripts/estilos inline ni orígenes externos.
- [ ] Verificar cada release con el checklist del preview ([infraestructura](../infraestructura.md#headers-de-seguridad-csp)).
- [ ] Revisar este ADR si se deja de usar el rewrite de `/api`.

---

## 7. References

- Issues: [#223](https://github.com/Benji-9/SmartElevate/issues/223), [#204](https://github.com/Benji-9/SmartElevate/issues/204).
- ADRs: [ADR-0003](0003-deploy-frontend-vercel-via-github-actions.md), [ADR-0019](0019-refresh-token-en-cookie-httponly.md).
- Docs: [`frontend/vercel.json`](../../frontend/vercel.json), [infraestructura](../infraestructura.md#headers-de-seguridad-csp).
