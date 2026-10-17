# 0024. Movilidad reducida: la marca viene de OFALU, SmartElevate no maneja certificados

- **Estado:** Aceptado
- **Fecha:** 2026-10-16
- **Issues:** [#122](https://github.com/Benji-9/SmartElevate/issues/122), [#123](https://github.com/Benji-9/SmartElevate/issues/123), [#24](https://github.com/Benji-9/SmartElevate/issues/24), [#26](https://github.com/Benji-9/SmartElevate/issues/26)
- **Autores:** equipo SmartElevate (reunión del 16/10/2026)

Supersede parcialmente los ADR [0009](0009-autenticacion-y-prioridad.md) (certificado y datos de salud) y [0013](0013-email-con-brevo-y-storage-en-r2.md) (storage).

## Contexto

El [ADR 0009](0009-autenticacion-y-prioridad.md) previó que la movilidad reducida se acreditara con un certificado subido por la persona, revisado por un `REVIEWER`, con vencimiento y borrado al resolver. Eso nos obligaba a tratar **datos de salud** (Ley 25.326: consentimiento expreso, finalidad limitada, medidas de seguridad) y a sostener un bucket privado en Cloudflare R2 ([ADR 0013](0013-email-con-brevo-y-storage-en-r2.md)). La validación legal de ese tratamiento ([#24](https://github.com/Benji-9/SmartElevate/issues/24)) seguía abierta.

En la reunión del 16/10/2026 el equipo confirmó que UADE, a través de OFALU (Oficina de Alumnos), ya recibe el aviso de las personas con movilidad reducida (temporal o permanente) y las marca. Los certificados médicos los maneja UADE.

## Opciones consideradas

1. **Subir certificados en la app** (postura anterior): bucket privado, URLs firmadas cortas, revisión por `REVIEWER`, vencimiento y borrado. Pros: SmartElevate no depende de nadie para marcar. Contras: duplica un trámite que UADE ya hace, nos obliga a tratar datos de salud sin validación legal, suma storage, costos y superficie de ataque.
2. **La marca viene de OFALU** (elegida): la app no ve certificados ni datos de salud. Pros: sin datos sensibles, menos código y menos infraestructura, la fuente de verdad es la institución. Contras: depende de una integración que todavía no existe y cuyos detalles hay que acordar.

## Decisión

SmartElevate **no sube, guarda, revisa ni muestra certificados** y no toca datos de salud sensibles. La marca de movilidad reducida la pone UADE vía OFALU. A futuro habrá una integración con OFALU para recibirla; formato, vigencia y temporal vs permanente se definen con OFALU. Mientras tanto, el perfil solo informa a la persona que lo avise en OFALU.

## Consecuencias

- Se cierran por falta de objeto [#122](https://github.com/Benji-9/SmartElevate/issues/122) (subida), [#123](https://github.com/Benji-9/SmartElevate/issues/123) (revisión, vencimiento y borrado) y [#24](https://github.com/Benji-9/SmartElevate/issues/24) (tratamiento de datos de salud). El bloqueante B-13 queda resuelto.
- El backend conserva la categoría `REDUCED_MOBILITY` y el ascensor dedicado IND1 06, pero **ningún endpoint asigna esa categoría**. Hasta la integración con OFALU nadie puede quedar marcado con movilidad reducida en la app. **Pregunta abierta:** si un `ADMIN` la marca a mano mientras tanto. No está decidido ni implementado.
- El rol `REVIEWER` ("revisa certificados") queda sin función. Se conserva en el código y la base por ahora; se evalúa quitarlo.
- El bucket R2 `smartelevate-certificados` y las variables `APP_STORAGE_*` ya no se usan: se pueden borrar a mano de Cloudflare y Render. Del ADR 0013 sigue vigente la parte de email con Brevo.
- La tabla `user_priority` y la migración V10 no cambian (no se editan migraciones mergeadas).
- La tarjeta "Acceso prioritario" del perfil pasa a ser solo informativa y las páginas legales ya no hablan de datos de salud.
- La integración con OFALU queda en Fase 2 ([alcance del MVP](../alcance-mvp.md)); cuando se defina, va en un ADR nuevo.
