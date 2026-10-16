# 0023. Defensa CSRF del refresh por cookie

- **Estado:** Aceptado
- **Fecha:** 2026-10-15
- **Issues:** [#204](https://github.com/Benji-9/SmartElevate/issues/204). Complementa el [ADR 0019](0019-refresh-token-en-cookie-httponly.md).
- **Autores:** @Benji-9

## Contexto

El [ADR 0019](0019-refresh-token-en-cookie-httponly.md) mueve el refresh token a una cookie `HttpOnly; Secure; SameSite=Lax` servida por el proxy del mismo origen. Una cookie viaja sola, así que los endpoints que la leen quedan expuestos a CSRF. Son solo dos: `POST /api/auth/refresh` (un sitio ajeno podría gastar el token y rotarlo, cerrando la sesión por reuso) y `POST /api/auth/logout` (cerrar la sesión de alguien). El resto de la API usa `Authorization: Bearer` desde memoria y no depende de cookies, así que no es CSRF-able. Spring Security tiene `csrf` desactivado por eso.

## Opciones consideradas

1. **Solo `SameSite=Lax`**: ya impide enviar la cookie en `POST` cross-site, pero depende del navegador y no cubre subdominios del mismo sitio.
2. **Token CSRF sincronizado (doble submit)**: robusto, pero agrega estado y un endpoint o cookie extra para dos rutas con efecto menor.
3. **Header custom fijo + verificación de `Origin`**: un formulario o un `fetch` cross-site simple no puede mandar `X-Requested-With`; ponerlo exige preflight CORS, y el backend solo permite los orígenes de `CORS_ALLOWED_ORIGINS`. Si el navegador manda `Origin` (siempre lo hace en un `POST` `fetch`), se lo compara con esa lista.

## Decisión

Opción **3**, además de `SameSite=Lax`. `/auth/refresh` y `/auth/logout` responden **403** si falta `X-Requested-With: SmartElevate` o si hay un `Origin` fuera de `CORS_ALLOWED_ORIGINS`; la comprobación va antes de leer la cookie, así un pedido rechazado no gasta el token. La comparación con `CORS_ALLOWED_ORIGINS` es **exacta**: sin barra final, sin path y en minúsculas, tal como el navegador manda el `Origin` (`https://smart-elevate.vercel.app`, no `https://Smart-Elevate.vercel.app/`). Un `Origin` ausente se acepta porque el header custom ya cubre ese caso (un cliente que no es navegador no es un vector CSRF).

CORS queda con orígenes explícitos y **sin** `allowCredentials`: con el proxy del mismo origen (Vercel en prod, proxy de Vite en dev) el navegador nunca hace cross-origin con la cookie. Si algún día se llamara al backend directo desde otro origen habría que habilitarlo, y este ADR se revisa.

La cookie se llama `refresh_token`, con `Path=/api/auth` (no viaja al resto de la API) y `Max-Age` igual a `JWT_REFRESH_TTL`. Nombre, `Secure`, `SameSite` y `Path` son propiedades técnicas (`app.security.refresh-cookie.*`, ADR 0017) con override `APP_REFRESH_COOKIE_*`; `Secure` es `true` por defecto y el perfil `dev` lo apaga para `http://localhost`.

**Varias pestañas.** La cookie se comparte entre pestañas, pero la promesa que evita canjes simultáneos (`refreshing`) es por pestaña. Al abrir N pestañas a la vez (o vencer el access token en varias), todas mandarían el mismo refresh: la primera rota, y las demás parecerían un reuso y revocarían la familia, cerrando la sesión sin culpa del usuario. Solución en el front: el canje corre dentro de `navigator.locks.request('smartelevate-refresh', ...)`, así la segunda pestaña manda la cookie ya rotada. Sin `navigator.locks` (jsdom, navegadores viejos) se canjea sin lock. La detección de reuso del backend **no se relaja**: un token robado sigue revocando la sesión.

**401 de refresh.** Cuando el refresh es inválido, vencido, revocado o reusado (o falta la cookie), el 401 trae además un `Set-Cookie` que expira la cookie (mismos nombre, `Path` y `SameSite`), para que el navegador no siga mandando un token muerto.

## Consecuencias

- El login y el refresh ya no devuelven `refreshToken` en el body; el logout y el refresh no tienen body.
- El front manda `credentials: 'include'` y el header en esas dos llamadas. Un cliente nuevo debe hacerlo igual.
- `X-Requested-With` es un secreto público: no autentica, solo prueba que el pedido salió de código que pasó el control de CORS.
- Rechazos y rotaciones se cubren con tests de integración (`SessionApiTest`).
