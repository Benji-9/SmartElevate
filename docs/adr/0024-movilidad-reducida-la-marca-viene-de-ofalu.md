# ADR-0024: Movilidad reducida: la marca viene de OFALU, SmartElevate no maneja certificados

## Metadata

- **Status:** Accepted
- **Date:** 2026-10-16
- **Deciders:** equipo SmartElevate (reunión del 16/10/2026)
- **Owner:** equipo SmartElevate
- **Related Issues:** [#122](https://github.com/Benji-9/SmartElevate/issues/122), [#123](https://github.com/Benji-9/SmartElevate/issues/123), [#24](https://github.com/Benji-9/SmartElevate/issues/24), [#26](https://github.com/Benji-9/SmartElevate/issues/26)
- **Related ADRs:** [ADR-0009](0009-autenticacion-y-prioridad.md) (supersede parcialmente: certificado y datos de salud), [ADR-0013](0013-email-con-brevo-y-storage-en-r2.md) (supersede parcialmente: storage)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0024-movilidad-reducida-la-marca-viene-de-ofalu.md`)

---

## 1. Context and Problem Statement

### Context

El [ADR-0009](0009-autenticacion-y-prioridad.md) previó que la movilidad reducida se acreditara con un certificado subido por la persona, revisado por un `REVIEWER`, con vencimiento y borrado al resolver. Eso nos obligaba a tratar **datos de salud** (Ley 25.326: consentimiento expreso, finalidad limitada, medidas de seguridad) y a sostener un bucket privado en Cloudflare R2 ([ADR-0013](0013-email-con-brevo-y-storage-en-r2.md)). La validación legal de ese tratamiento ([#24](https://github.com/Benji-9/SmartElevate/issues/24)) seguía abierta.

En la reunión del 16/10/2026 el equipo confirmó que UADE, a través de OFALU (Oficina de Alumnos), ya recibe el aviso de las personas con movilidad reducida (temporal o permanente) y las marca. Los certificados médicos los maneja UADE.

### Problem

¿Quién acredita y marca la movilidad reducida: la app con certificados subidos por las personas, o la institución?

---

## 2. Decision Drivers

- Evitar tratar datos de salud (Ley 25.326) sin validación legal.
- No duplicar un trámite que UADE ya hace.
- Menos infraestructura, costos y superficie de ataque.
- La fuente de verdad de la marca debería ser la institución.

---

## 3. Considered Options

### Option A: Subir certificados en la app (postura anterior)

Bucket privado, URLs firmadas cortas, revisión por `REVIEWER`, vencimiento y borrado.

**Pros:**
- SmartElevate no depende de nadie para marcar.

**Cons:**
- Duplica un trámite que UADE ya hace.
- Nos obliga a tratar datos de salud sin validación legal.
- Suma storage, costos y superficie de ataque.

### Option B: La marca viene de OFALU

La app no ve certificados ni datos de salud.

**Pros:**
- Sin datos sensibles.
- Menos código y menos infraestructura.
- La fuente de verdad es la institución.

**Cons:**
- Depende de una integración que todavía no existe y cuyos detalles hay que acordar.

---

## 4. Decision Outcome

**Chosen Option:** B, la marca viene de OFALU.

### Rationale

SmartElevate **no sube, guarda, revisa ni muestra certificados** y no toca datos de salud sensibles. La marca de movilidad reducida la pone UADE vía OFALU. A futuro habrá una integración con OFALU para recibirla; formato, vigencia y temporal vs permanente se definen con OFALU. Mientras tanto, el perfil solo informa a la persona que lo avise en OFALU.

---

## 5. Consequences

### Positive Consequences

- Se cierran por falta de objeto [#122](https://github.com/Benji-9/SmartElevate/issues/122) (subida), [#123](https://github.com/Benji-9/SmartElevate/issues/123) (revisión, vencimiento y borrado) y [#24](https://github.com/Benji-9/SmartElevate/issues/24) (tratamiento de datos de salud). El bloqueante B-13 queda resuelto.
- La tarjeta "Acceso prioritario" del perfil pasa a ser solo informativa y las páginas legales ya no hablan de datos de salud.

### Negative Consequences

- Hasta la integración con OFALU nadie puede quedar marcado con movilidad reducida en la app.
- **Pregunta abierta:** si un `ADMIN` la marca a mano mientras tanto. No está decidido ni implementado.
- El rol `REVIEWER` ("revisa certificados") queda sin función. Se conserva en el código y la base por ahora; se evalúa quitarlo.

### Technical Implications

- El backend conserva la categoría `REDUCED_MOBILITY` y el ascensor dedicado IND1 06, pero **ningún endpoint asigna esa categoría**.
- El bucket R2 `smartelevate-certificados` y las variables `APP_STORAGE_*` ya no se usan: se pueden borrar a mano de Cloudflare y Render. Del ADR-0013 sigue vigente la parte de email con Brevo.
- La tabla `user_priority` y la migración V10 no cambian (no se editan migraciones mergeadas).
- La integración con OFALU queda en Fase 2 ([alcance del MVP](../alcance-mvp.md)); cuando se defina, va en un ADR nuevo.

---

## 6. Implementation and Validation

- [x] Certificados, revisión y datos de salud fuera de la app; perfil informativo y páginas legales actualizadas.
- [x] Ningún endpoint asigna `REDUCED_MOBILITY` (no agregar uno ni volver a tocar certificados sin ADR nuevo).
- [ ] Borrar a mano el bucket R2 `smartelevate-certificados` y las variables `APP_STORAGE_*` de Cloudflare y Render.
- [ ] Evaluar quitar el rol `REVIEWER`.
- [ ] Decidir si un `ADMIN` puede marcarla a mano mientras tanto (pregunta abierta).
- [ ] Acordar con OFALU la integración (formato, vigencia, temporal vs permanente) y registrarla en un ADR nuevo (Fase 2).

---

## 7. References

- [ADR-0009](0009-autenticacion-y-prioridad.md)
- [ADR-0013](0013-email-con-brevo-y-storage-en-r2.md)
- [Alcance del MVP](../alcance-mvp.md)
- Issues [#122](https://github.com/Benji-9/SmartElevate/issues/122), [#123](https://github.com/Benji-9/SmartElevate/issues/123), [#24](https://github.com/Benji-9/SmartElevate/issues/24), [#26](https://github.com/Benji-9/SmartElevate/issues/26)
