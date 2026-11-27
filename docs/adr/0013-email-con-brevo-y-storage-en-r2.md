# ADR-0013: Email con Brevo (y storage en R2, descartado)

## Metadata

- **Status:** Accepted (la parte de storage en R2 fue reemplazada por [ADR-0024](0024-movilidad-reducida-la-marca-viene-de-ofalu.md); el email con Brevo sigue vigente)
- **Date:** 2026-10-06
- **Deciders:** equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** [#25](https://github.com/Benji-9/SmartElevate/issues/25), [#26](https://github.com/Benji-9/SmartElevate/issues/26), [#24](https://github.com/Benji-9/SmartElevate/issues/24)
- **Related ADRs:** [ADR-0009](0009-autenticacion-y-prioridad.md), [ADR-0024](0024-movilidad-reducida-la-marca-viene-de-ofalu.md)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0013-email-con-brevo-y-storage-en-r2.md`)

> **Nota (descartado por ADR-0024):** este ADR también eligió **Cloudflare R2** (bucket privado `smartelevate-certificados`, ENAM, clase Standard, con token de API limitado al bucket) para los certificados de movilidad reducida. El ADR-0024 sacó los certificados de la app, así que R2 y las variables `APP_STORAGE_*` ya no se usan y se pueden borrar a mano de Cloudflare y Render. Se conserva el aviso que regía mientras estuvo previsto: **R2 guarda los archivos en EE.UU., y no se subían certificados reales hasta resolver B-13** (Ley 25.326, [#24](https://github.com/Benji-9/SmartElevate/issues/24)). Las alternativas de storage evaluadas (Supabase Storage: 1 GB y pausa tras 1 semana sin uso; AWS S3: el free tier vence a los 12 meses; guardar el archivo en Postgres: no cumplía lo del ADR-0009 y sumaba datos de salud a cada backup) ya no aplican.

---

## 1. Context and Problem Statement

### Context

El [ADR-0009](0009-autenticacion-y-prioridad.md) pide **email** para verificar las cuentas @uade.edu.ar por link (B-14, [#25](https://github.com/Benji-9/SmartElevate/issues/25)).

Restricciones: costo $0 y **sin dominio propio**. `smart-elevate.vercel.app` no permite cargar registros DNS. Además, desde 02/2024 Gmail, Yahoo y Microsoft exigen que el dominio del remitente esté autenticado (DKIM y DMARC).

### Problem

¿Qué proveedor de email usamos para mandar los links de verificación a cuentas @uade.edu.ar sin pagar y sin dominio propio?

---

## 2. Decision Drivers

- Costo $0.
- Sin dominio propio (no se pueden cargar registros DNS).
- Entregabilidad: Gmail, Yahoo y Microsoft exigen remitente autenticado (DKIM y DMARC).
- Poder cambiar de proveedor sin tocar código.

---

## 3. Considered Options

### Option A: Brevo

300 mails por día gratis, SMTP y API. Sin dominio, reescribe el remitente a `@brevosend.com`, un dominio autenticado por Brevo.

**Pros:**
- 300 mails por día gratis.
- Funciona sin dominio propio.

**Cons:**
- El remitente se ve como `@brevosend.com`.

### Option B: Resend

100 mails por día gratis y mejor API.

**Cons:**
- **Exige dominio propio** para mandar a terceros (descartado por esto).

### Option C: SMTP de una cuenta de Gmail

**Pros:**
- No requiere dominio.

**Cons:**
- Depende de contraseñas de aplicación de una cuenta personal, y Google puede limitar o bloquear el envío automatizado.

---

## 4. Decision Outcome

**Chosen Option:** A, Brevo por SMTP.

### Rationale

Con remitente verificado `smartelevate.uade@gmail.com`. Se usa `spring-boot-starter-mail` y no el SDK de Brevo, así que cambiar de proveedor es cambiar variables. Prueba del 2026-10-06: un mail a @uade.edu.ar llegó a la bandeja de entrada, con remitente `smartelevate.uade@12377968.brevosend.com`. Resend se descartó por exigir dominio propio y Gmail por depender de una cuenta personal.

La configuración y las variables están en [`docs/infraestructura.md`](../infraestructura.md#email-con-brevo).

---

## 5. Consequences

### Positive Consequences

- **300 mails por día** alcanzan para verificar cuentas en el MVP.
- Cambiar de proveedor es solo cambiar variables.

### Negative Consequences

- **El remitente se ve como `@brevosend.com`.** Si los mails empiezan a caer en spam, la solución es un dominio propio (~USD 10 por año) autenticado en Brevo. Solo cambia `APP_MAIL_FROM`, no hay que tocar código.
- Un lanzamiento masivo en un solo día superaría los 300 mails.

### Technical Implications

- Variables: `SPRING_MAIL_*` (Spring las lee sin configuración extra) y `APP_MAIL_FROM`. Al implementar, hay que activar STARTTLS en `spring.mail.properties`.
- Las variables ya están cargadas en Render, pero el código que las usa llega con la autenticación ([B-05](../bloqueantes.md)).

---

## 6. Implementation and Validation

- [x] Cuenta de Brevo con remitente verificado y prueba de entrega a @uade.edu.ar (2026-10-06).
- [x] Variables `SPRING_MAIL_*` y `APP_MAIL_FROM` cargadas en Render.
- [ ] Activar STARTTLS en `spring.mail.properties` al implementar el envío (llega con la autenticación, B-05).
- [ ] Si los mails caen en spam, evaluar dominio propio autenticado en Brevo.

---

## 7. References

- [ADR-0009](0009-autenticacion-y-prioridad.md)
- [ADR-0024](0024-movilidad-reducida-la-marca-viene-de-ofalu.md)
- [`docs/infraestructura.md`](../infraestructura.md#email-con-brevo)
- [Bloqueantes](../bloqueantes.md)
- Issues [#25](https://github.com/Benji-9/SmartElevate/issues/25), [#26](https://github.com/Benji-9/SmartElevate/issues/26), [#24](https://github.com/Benji-9/SmartElevate/issues/24)
