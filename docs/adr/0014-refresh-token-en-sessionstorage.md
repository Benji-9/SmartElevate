# 0014. Refresh token del frontend en sessionStorage

- **Estado:** Aceptado
- **Fecha:** 2026-10-13
- **Issues:** [#192](https://github.com/Benji-9/SmartElevate/issues/192), [#120](https://github.com/Benji-9/SmartElevate/issues/120)
- **Autores:** @Benji-9

## Contexto

El backend (#120) devuelve el refresh token **en el body JSON** (`TokenResponse`) y lo recibe en el body de `/auth/refresh` y `/auth/logout`; no usa cookie HttpOnly. El refresh es opaco, de un solo uso (rotación), y reusar uno canjeado revoca toda la sesión. El frontend debe decidir dónde guardarlo.

## Opciones consideradas

1. **Solo en memoria**: no queda rastro en el navegador, pero recargar la pestaña cierra la sesión.
2. **`sessionStorage`**: sobrevive a recargar, se borra al cerrar la pestaña. Un XSS podría leerlo.
3. **`localStorage`**: sesión entre pestañas y reinicios, mayor ventana de exposición.
4. **Cookie HttpOnly**: la más segura frente a XSS, pero requiere cambiar el backend (cookie + CSRF + CORS con credenciales).

## Decisión

Opción **2**. El access token (~15 min) vive solo en memoria; el refresh token en `sessionStorage` (`smartelevate.refresh`). Al abrir o recargar la pestaña, `restoreSession` canjea el refresh y pide el usuario al servidor. Un 401 con access vencido dispara **un único** refresh compartido (el segundo canje revocaría la sesión) y reintenta; si el refresh falla, se borra todo y se avisa que la sesión venció. Logout manda el refresh para revocarlo y limpia igual aunque falle la red.

## Consecuencias

- Una sesión por pestaña: abrir otra pestaña pide login de nuevo (aceptable en el MVP).
- Riesgo residual ante XSS (hallazgo MEDIUM de la revisión de seguridad). Mitigaciones en el front: sin `innerHTML`/`dangerouslySetInnerHTML`, sin loguear tokens, el access solo en memoria, y el refresh se borra al cerrar sesión y ante un refresh inválido. Falta una CSP en el hosting (Vercel). Pasar a cookie HttpOnly/Secure/SameSite (opción 4) requiere un cambio de backend (issue de seguimiento propuesto): se hará si el riesgo no se acepta.
- El usuario (rol, prioridad) se pide al servidor; nunca se lee de claims del JWT (ADR 0009).
