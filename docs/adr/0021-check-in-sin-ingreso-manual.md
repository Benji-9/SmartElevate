# 0021. Check-in sin ingreso manual del código en el MVP

- **Estado:** Aceptado
- **Fecha:** 2026-10-14
- **Issues:** [#238](https://github.com/Benji-9/SmartElevate/issues/238), parte de [#219](https://github.com/Benji-9/SmartElevate/issues/219). Aclara el [ADR 0016](0016-qr-firmado-con-hmac-y-kiosco-web.md).
- **Autores:** @Benji-9

## Contexto

El [ADR 0016](0016-qr-firmado-con-hmac-y-kiosco-web.md) dejó el ingreso manual del código (`/check-in/codigo`) como respaldo si el kiosco se queda sin red o se apaga. Con el token ya implementado ([#230](https://github.com/Benji-9/SmartElevate/issues/230)), lo que se escanea es `base64(elevatorId|kioskId|exp).firma`: unos 80 caracteres opacos que rotan cada ~30 s. Nadie puede tipearlo, y el respaldo tampoco sirve en el caso que tenía que cubrir: sin red, el kiosco no tiene token para mostrar.

## Opciones consideradas

1. **Código corto aparte** (p. ej. 6 caracteres, rotando con el QR): se puede tipear, pero es otro secreto que firmar, validar y limitar contra fuerza bruta, y tampoco existe si el kiosco está caído.
2. **Tipear el token completo:** no es usable.
3. **Sacar el ingreso manual del MVP:** sin respaldo; la caída de un kiosco se acepta como limitación.

## Decisión

Opción 3 (decisión del equipo, 14/10). El check-in del MVP es solo escaneando el QR del kiosco. Si el kiosco se queda sin red o se apaga, en ese ascensor no hay check-in mientras dure la caída.

## Consecuencias

- Se borran `/check-in/codigo`, el botón "Ingresar código manualmente" y sus tests. El ingreso manual pasa a "Fuera del MVP" en [alcance-mvp.md](../alcance-mvp.md).
- Sin cámara o sin permiso, la app explica cómo habilitarla; no ofrece otra vía.
- Una caída del kiosco suma no-shows que no son culpa del usuario. Si pasa seguido, la Fase 2 puede sumar un código corto (opción 1) o que el admin anule las faltas de esa franja.
- Para que el QR no quede atado al ascensor equivocado, `GET /api/kiosks/me/qr-token` devuelve `elevatorId` y el kiosco no muestra el QR si no coincide con el de su URL.
