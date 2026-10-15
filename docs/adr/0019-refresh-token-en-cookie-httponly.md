# 0019. Refresh token en cookie HttpOnly con proxy del mismo origen

- **Estado:** Propuesto
- **Fecha:** 2026-10-14
- **Issues:** [#204](https://github.com/Benji-9/SmartElevate/issues/204). Cuando se implemente, reemplaza el [ADR 0014](0014-refresh-token-en-sessionstorage.md).
- **Autores:** @Benji-9

## Contexto

El [ADR 0014](0014-refresh-token-en-sessionstorage.md) guardó el refresh token en `sessionStorage` y aceptó el riesgo ante XSS, con la cookie HttpOnly como mejora si el riesgo no se aceptaba. El equipo decidió el 14/10 hacer la mejora ([#204](https://github.com/Benji-9/SmartElevate/issues/204)). Una cookie entre dominios distintos (Vercel y Render) pediría CORS con credenciales y protección CSRF propia.

## Opciones consideradas

1. **Seguir con `sessionStorage`**: no hay que cambiar nada, pero un XSS puede leer el refresh.
2. **Cookie entre dominios** (`SameSite=None`) directo contra Render: CORS con credenciales, token CSRF y depende de cookies de terceros, que los navegadores bloquean cada vez más.
3. **Cookie del mismo origen**: el navegador habla solo con Vercel, que ya reenvía `/api/*` al backend con el rewrite de `vercel.json`. La cookie es de primera parte.

## Decisión

Opción **3**: el backend manda el refresh en una cookie **`HttpOnly; Secure; SameSite=Lax`**, servida a través del proxy del mismo origen de `vercel.json`. El access token sigue solo en memoria.

## Consecuencias

- Un XSS ya no puede leer el refresh. `SameSite=Lax` frena el envío de la cookie desde otros sitios en pedidos `POST`.
- Cambia el contrato de `/api/auth/login`, `/refresh` y `/logout` (el refresh deja de viajar en el body) y el front deja de usar `sessionStorage`.
- En dev, el proxy de Vite cumple el mismo papel que el rewrite de Vercel. Llamar a Render directo (sin proxy) deja de mantener la sesión.
- Al implementarse, el ADR 0014 pasa a "Reemplazado por 0019".
