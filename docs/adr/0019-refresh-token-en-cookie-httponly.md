# ADR-0019: Refresh token en cookie HttpOnly con proxy del mismo origen

## Metadata

- **Status:** Accepted
- **Date:** 2026-10-14
- **Deciders:** Equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** [#204](https://github.com/Benji-9/SmartElevate/issues/204), [#192](https://github.com/Benji-9/SmartElevate/issues/192), [#120](https://github.com/Benji-9/SmartElevate/issues/120)
- **Related ADRs:** Absorbe ADR-0014 (refresh token en `sessionStorage`, 2026-10-13, que este ADR reemplaza) y ADR-0023 (defensa CSRF del refresh por cookie, 2026-10-15); [ADR-0003](0003-deploy-frontend-vercel-via-github-actions.md); [ADR-0009](0009-autenticacion-y-prioridad.md); [ADR-0017](0017-donde-viven-los-parametros.md); [ADR-0022](0022-csp-y-headers-de-seguridad-en-vercel.md)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0019-refresh-token-en-cookie-httponly.md`)

---

## 1. Context and Problem Statement

### Context

El backend ([#120](https://github.com/Benji-9/SmartElevate/issues/120)) devolvía el refresh token **en el body JSON** (`TokenResponse`) y lo recibía en el body de `/auth/refresh` y `/auth/logout`. El refresh es opaco, de un solo uso (rotación), y reusar uno canjeado revoca toda la sesión.

El ADR-0014 (2026-10-13, [#192](https://github.com/Benji-9/SmartElevate/issues/192)) guardó el refresh en `sessionStorage` y aceptó el riesgo ante XSS (hallazgo MEDIUM de la revisión de seguridad), con la cookie HttpOnly como mejora si el riesgo no se aceptaba. El equipo decidió el 14/10 hacer la mejora ([#204](https://github.com/Benji-9/SmartElevate/issues/204)). Una cookie entre dominios distintos (Vercel y Render) pediría CORS con credenciales y protección CSRF propia.

Una cookie viaja sola, así que los endpoints que la leen quedan expuestos a CSRF. Son solo dos: `POST /api/auth/refresh` (un sitio ajeno podría gastar el token y rotarlo, cerrando la sesión por reuso) y `POST /api/auth/logout` (cerrar la sesión de alguien). El resto de la API usa `Authorization: Bearer` desde memoria y no depende de cookies, así que no es CSRF-able. Spring Security tiene `csrf` desactivado por eso.

### Problem

¿Dónde guarda el frontend el refresh token de modo que un XSS no lo pueda leer, sin pagar la complejidad de cookies entre dominios, y cómo se defienden de CSRF los dos endpoints que pasan a leer la cookie?

---

## 2. Decision Drivers

- Que un XSS no pueda leer el refresh token.
- Evitar CORS con credenciales y cookies de terceros (los navegadores las bloquean cada vez más).
- Vercel ya reenvía `/api/*` al backend con el rewrite de `vercel.json`; en dev, el proxy de Vite.
- Sin estado ni endpoints extra para dos rutas con efecto menor.
- Que el rechazo de un pedido sospechoso no gaste el token.
- Que abrir varias pestañas no cierre la sesión sin culpa del usuario.
- Mantener la detección de reuso del backend (un token robado sigue revocando la sesión).

---

## 3. Considered Options

### Dónde guardar el refresh

#### Option 1: Solo en memoria

**Pros:**
- No queda rastro en el navegador.

**Cons:**
- Recargar la pestaña cierra la sesión.

#### Option 2: `sessionStorage` (ADR-0014, reemplazado)

Sobrevive a recargar, se borra al cerrar la pestaña. El access token (~15 min) vivía solo en memoria y el refresh en `smartelevate.refresh`.

**Pros:**
- No hay que cambiar nada en el backend.

**Cons:**
- Un XSS puede leer el refresh.
- Una sesión por pestaña: abrir otra pide login de nuevo.
- Mitigaciones que tenía en el front (siguen valiendo): sin `innerHTML`/`dangerouslySetInnerHTML`, sin loguear tokens, el access solo en memoria, y el refresh se borra al cerrar sesión y ante un refresh inválido.

#### Option 3: `localStorage`

**Pros:**
- Sesión entre pestañas y reinicios.

**Cons:**
- Mayor ventana de exposición ante XSS.

#### Option 4: Cookie entre dominios (`SameSite=None`) directo contra Render

**Cons:**
- CORS con credenciales, token CSRF y depende de cookies de terceros, que los navegadores bloquean cada vez más.

#### Option 5: Cookie del mismo origen, servida por el proxy de Vercel

El navegador habla solo con Vercel, que ya reenvía `/api/*` al backend. La cookie es de primera parte.

**Pros:**
- Un XSS no la lee; sin CORS con credenciales.

### Defensa CSRF de `/auth/refresh` y `/auth/logout`

#### CSRF 1: Solo `SameSite=Lax`

**Pros:**
- Ya impide enviar la cookie en `POST` cross-site.

**Cons:**
- Depende del navegador y no cubre subdominios del mismo sitio.

#### CSRF 2: Token CSRF sincronizado (doble submit)

**Pros:**
- Robusto.

**Cons:**
- Agrega estado y un endpoint o cookie extra para dos rutas con efecto menor.

#### CSRF 3: Header custom fijo + verificación de `Origin`

Un formulario o un `fetch` cross-site simple no puede mandar `X-Requested-With`; ponerlo exige preflight CORS, y el backend solo permite los orígenes de `CORS_ALLOWED_ORIGINS`. Si el navegador manda `Origin` (siempre lo hace en un `POST` `fetch`), se lo compara con esa lista.

**Pros:**
- Sin estado extra.

---

## 4. Decision Outcome

**Chosen Option:** Option 5 (cookie del mismo origen) con CSRF 3 (header custom + `Origin`) además de `SameSite=Lax`.

### Rationale

**Cookie.** El backend manda el refresh en una cookie **`HttpOnly; Secure; SameSite=Lax`**, servida a través del proxy del mismo origen de `vercel.json`. El access token sigue solo en memoria. Responde al driver principal (XSS) sin CORS con credenciales.

**Defensa CSRF** (ex ADR-0023, 2026-10-15):

- `/auth/refresh` y `/auth/logout` responden **403** si falta `X-Requested-With: SmartElevate` o si hay un `Origin` fuera de `CORS_ALLOWED_ORIGINS`. La comprobación va antes de leer la cookie, así un pedido rechazado no gasta el token.
- La comparación con `CORS_ALLOWED_ORIGINS` es **exacta**: sin barra final, sin path y en minúsculas, tal como el navegador manda el `Origin` (`https://smart-elevate.vercel.app`, no `https://Smart-Elevate.vercel.app/`).
- Un `Origin` ausente se acepta porque el header custom ya cubre ese caso (un cliente que no es navegador no es un vector CSRF).
- CORS queda con orígenes explícitos y **sin** `allowCredentials`: con el proxy del mismo origen (Vercel en prod, proxy de Vite en dev) el navegador nunca hace cross-origin con la cookie. Si algún día se llamara al backend directo desde otro origen habría que habilitarlo, y este ADR se revisa.

**Cookie: propiedades.** Se llama `refresh_token`, con `Path=/api/auth` (no viaja al resto de la API) y `Max-Age` igual a `JWT_REFRESH_TTL`. Nombre, `Secure`, `SameSite` y `Path` son propiedades técnicas (`app.security.refresh-cookie.*`, [ADR-0017](0017-donde-viven-los-parametros.md)) con override `APP_REFRESH_COOKIE_*`; `Secure` es `true` por defecto y el perfil `dev` lo apaga para `http://localhost`.

**Varias pestañas.** La cookie se comparte entre pestañas, pero la promesa que evita canjes simultáneos (`refreshing`) es por pestaña. Al abrir N pestañas a la vez (o vencer el access token en varias), todas mandarían el mismo refresh: la primera rota, y las demás parecerían un reuso y revocarían la familia, cerrando la sesión sin culpa del usuario. Solución en el front: el canje corre dentro de `navigator.locks.request('smartelevate-refresh', ...)`, así la segunda pestaña manda la cookie ya rotada. Sin `navigator.locks` (jsdom, navegadores viejos) se canjea sin lock. La detección de reuso del backend **no se relaja**: un token robado sigue revocando la sesión.

**401 de refresh.** Cuando el refresh es inválido, vencido, revocado o reusado (o falta la cookie), el 401 trae además un `Set-Cookie` que expira la cookie (mismos nombre, `Path` y `SameSite`), para que el navegador no siga mandando un token muerto.

**Comportamiento heredado del ADR-0014** (sigue vigente): al abrir o recargar la pestaña, `restoreSession` canjea el refresh y pide el usuario al servidor. Un 401 con access vencido dispara **un único** refresh compartido (el segundo canje revocaría la sesión) y reintenta; si el refresh falla, se borra todo y se avisa que la sesión venció. Logout revoca el refresh y limpia igual aunque falle la red. El usuario (rol, prioridad) se pide al servidor; nunca se lee de claims del JWT ([ADR-0009](0009-autenticacion-y-prioridad.md)).

---

## 5. Consequences

### Positive Consequences

- Un XSS ya no puede leer el refresh. `SameSite=Lax` frena el envío de la cookie desde otros sitios en pedidos `POST`.
- Sesión compartida entre pestañas sin revocaciones espurias.

### Negative Consequences

- Llamar a Render directo (sin proxy) deja de mantener la sesión; en dev, el proxy de Vite cumple el mismo papel que el rewrite de Vercel.
- `X-Requested-With` es un secreto público: no autentica, solo prueba que el pedido salió de código que pasó el control de CORS.
- Un cliente nuevo debe mandar `credentials: 'include'` y el header en esas dos llamadas.
- Si se llamara al backend directo desde otro origen, habría que habilitar `allowCredentials` y revisar este ADR.

### Technical Implications

- Cambia el contrato de `/api/auth/login`, `/refresh` y `/logout`: el login y el refresh ya no devuelven `refreshToken` en el body; el logout y el refresh no tienen body.
- El front deja de usar `sessionStorage` y manda `credentials: 'include'` y `X-Requested-With: SmartElevate` en refresh y logout.
- CSP que acompaña esta decisión: [ADR-0022](0022-csp-y-headers-de-seguridad-en-vercel.md).

---

## 6. Implementation and Validation

- [x] Cookie `refresh_token` (`HttpOnly; Secure; SameSite=Lax`, `Path=/api/auth`) configurable con `app.security.refresh-cookie.*` / `APP_REFRESH_COOKIE_*`.
- [x] Chequeo de `X-Requested-With` y `Origin` en `/auth/refresh` y `/auth/logout` antes de leer la cookie.
- [x] `Set-Cookie` que expira la cookie en el 401 de refresh.
- [x] Lock `navigator.locks` en el front para el canje entre pestañas.
- [x] Rechazos y rotaciones cubiertos con tests de integración (`SessionApiTest`).
- [x] Front sin `sessionStorage` para el refresh.

---

## 7. References

- Issues: [#204](https://github.com/Benji-9/SmartElevate/issues/204), [#192](https://github.com/Benji-9/SmartElevate/issues/192), [#120](https://github.com/Benji-9/SmartElevate/issues/120).
- ADRs: [ADR-0003](0003-deploy-frontend-vercel-via-github-actions.md), [ADR-0009](0009-autenticacion-y-prioridad.md), [ADR-0017](0017-donde-viven-los-parametros.md), [ADR-0022](0022-csp-y-headers-de-seguridad-en-vercel.md).
