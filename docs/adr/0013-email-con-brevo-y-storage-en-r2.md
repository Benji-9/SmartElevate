# 0013. Email con Brevo y storage de certificados en Cloudflare R2

- **Estado:** Aceptado
- **Fecha:** 2026-10-06
- **Autores:** @Benji-9

## Contexto

El [ADR 0009](0009-autenticacion-y-prioridad.md) pide dos servicios externos:

- **Email** para verificar las cuentas @uade.edu.ar por link (B-14, [#25](https://github.com/Benji-9/SmartElevate/issues/25)).
- Un **bucket privado** con URLs firmadas cortas para los certificados de prioridad, que son datos de salud (B-15, [#26](https://github.com/Benji-9/SmartElevate/issues/26)).

Restricciones: costo $0 y **sin dominio propio**. `smart-elevate.vercel.app` no permite cargar registros DNS. Además, desde 02/2024 Gmail, Yahoo y Microsoft exigen que el dominio del remitente esté autenticado (DKIM y DMARC).

## Opciones consideradas

**Email:**

1. **Brevo**: 300 mails por día gratis, SMTP y API. Sin dominio, reescribe el remitente a `@brevosend.com`, un dominio autenticado por Brevo.
2. **Resend**: 100 mails por día gratis y mejor API, pero **exige dominio propio** para mandar a terceros.
3. **SMTP de una cuenta de Gmail**: no requiere dominio, pero depende de contraseñas de aplicación de una cuenta personal, y Google puede limitar o bloquear el envío automatizado.

**Storage:**

1. **Cloudflare R2**: 10 GB gratis, API de S3 con URLs firmadas y egreso gratis. Pide una tarjeta para activarse.
2. **Supabase Storage**: 1 GB, pero el proyecto se pausa tras 1 semana sin uso.
3. **AWS S3**: el free tier vence a los 12 meses.
4. **Guardar el archivo en Postgres**: no cumple lo del ADR 0009 (bucket privado y URLs firmadas) y suma datos de salud a cada backup de la base.

## Decisión

- **Email: Brevo por SMTP**, con remitente verificado `smartelevate.uade@gmail.com`. Se usa `spring-boot-starter-mail` y no el SDK de Brevo, así que cambiar de proveedor es cambiar variables. Prueba del 2026-10-06: un mail a @uade.edu.ar llegó a la bandeja de entrada, con remitente `smartelevate.uade@12377968.brevosend.com`.
- **Storage: Cloudflare R2**, bucket privado `smartelevate-certificados` (ENAM, clase Standard), con un token de API limitado a ese bucket.

La configuración y las variables están en [`docs/infraestructura.md`](../infraestructura.md#email-con-brevo).

## Consecuencias

- **El remitente se ve como `@brevosend.com`.** Si los mails empiezan a caer en spam, la solución es un dominio propio (~USD 10 por año) autenticado en Brevo. Solo cambia `APP_MAIL_FROM`, no hay que tocar código.
- **Configuración por variables:**
  - Mail: `SPRING_MAIL_*` (Spring las lee sin configuración extra) y `APP_MAIL_FROM`. Al implementar, hay que activar STARTTLS en `spring.mail.properties`.
  - Storage: `APP_STORAGE_*`. R2 usa la región `auto` en el cliente de S3.
- Las variables ya están cargadas en Render, pero el código que las usa llega con la autenticación ([B-05](../bloqueantes.md)).
- **300 mails por día** alcanzan para verificar cuentas en el MVP. Un lanzamiento masivo en un solo día los superaría.
- **R2 cobra si se pasa de 10 GB**, a la tarjeta de la cuenta de @Benji-9. Es improbable, porque los certificados se borran al resolverse.
- **Datos de salud:** R2 guarda los archivos en EE.UU. No se suben certificados reales hasta resolver B-13 (Ley 25.326, [#24](https://github.com/Benji-9/SmartElevate/issues/24)).
