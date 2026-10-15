# 0022. CSP y headers de seguridad en Vercel

- **Estado:** Propuesto
- **Fecha:** 2026-10-15
- **Issues:** [#223](https://github.com/Benji-9/SmartElevate/issues/223). Relacionado con [#204](https://github.com/Benji-9/SmartElevate/issues/204) y los ADR [0003](0003-deploy-frontend-vercel-via-github-actions.md), [0014](0014-refresh-token-en-sessionstorage.md) y [0019](0019-refresh-token-en-cookie-httponly.md).
- **Autores:** @Benji-9

## Contexto

El [ADR 0014](0014-refresh-token-en-sessionstorage.md) dejó la CSP como mitigación pendiente ante un XSS que lea el refresh token de `sessionStorage`. Auditoría del front (`npm run build`): el HTML no tiene scripts ni `<style>` inline, no hay orígenes externos (Inter sale de `@fontsource-variable/inter`, empaquetada en `dist/assets`), el QR del kiosco es un `<svg>` inline y los `smartelevate-logo*.svg` (~19 KB) salen como `/assets/*.svg` del mismo origen y solo `uade-logo.svg` (1,9 KB) queda inlineado como `data:image/svg+xml`; ambos pasan con `img-src 'self' data:`. El navegador solo habla con el mismo origen: `/api/*` lo reenvía Vercel al backend con un rewrite. React usa atributos `style=` (anchos de barras, `--value`).

## Decisión

Headers en [`frontend/vercel.json`](../../frontend/vercel.json), para todas las rutas:

- `Content-Security-Policy`: `default-src 'self'; script-src 'self'; style-src 'self'; style-src-attr 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; media-src 'self' blob:; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`.
- `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`.
- `Permissions-Policy: camera=(self), microphone=(), geolocation=(), payment=(), usb=()` (el check-in escanea con la cámara).
- HSTS: no se agrega, Vercel ya lo manda en los dominios `*.vercel.app` y en los propios.

Puntos a justificar:

- **Sin `unsafe-inline` en scripts ni en `<style>`.** Los `style=` de React son inevitables, así que se permiten solo como atributo con `style-src-attr 'unsafe-inline'`; las hojas de estilo siguen siendo `'self'`. Un atributo `style` no ejecuta código: el riesgo es de exfiltración por CSS, bajo.
- **`connect-src 'self'`** sin `${BACKEND_URL}`: el navegador nunca llama al backend directo, así que el placeholder no se usa en la CSP y el reemplazo del workflow no cambia (solo toca el rewrite de `/api`).
- Se aplica la CSP real y no `Report-Only`: no hay endpoint de reporte.

## Consecuencias

- Cualquier recurso externo nuevo (fuentes de Google, analytics, un CDN) se va a bloquear hasta sumarlo a la CSP en este archivo.
- Si #204 mantiene el proxy del mismo origen, `connect-src` sigue en `'self'`. Solo habría que sumar el origen del backend si se dejara de usar el rewrite, y ahí el placeholder `${BACKEND_URL}` tendría que reemplazarse también en la CSP.
- Riesgo: una violación no detectada rompe una pantalla en producción. Se mitiga con el checklist de verificación en el preview ([infraestructura](../infraestructura.md#headers-de-seguridad-csp)).
