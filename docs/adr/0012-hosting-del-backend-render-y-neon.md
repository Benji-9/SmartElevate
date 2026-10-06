# 0012. Hosting del backend en Render y Postgres en Neon

- **Estado:** Propuesto
- **Fecha:** 2026-10-06
- **Autores:** @Benji-9

## Contexto

Sin un backend público, el front en Vercel no llega a la API (bloqueante B-01, [#5](https://github.com/Benji-9/SmartElevate/issues/5)). La imagen Docker ya estaba lista (B-08).

Criterios: **costo $0** para el MVP, soporte de Docker, deploy desde GitHub y un Postgres que no venza ni se borre durante la cursada. Spring Boot en la JVM necesita ~512 MB de RAM, lo que descarta las máquinas de 256 MB.

Planes verificados el 2026-10-06:

| Opción | Plan gratuito |
| --- | --- |
| Render | Web Service free (0.1 CPU, 512 MB, 750 h/mes, duerme tras 15 min). Su Postgres free **vence a los 30 días** y después se borra |
| Railway | Sin plan gratis: USD 5 de crédito único y después USD 5/mes más uso |
| Fly.io | Sin plan gratis: solo un trial de 2 h o 7 días |
| Koyeb | Plan gratis cerrado a cuentas nuevas desde 02/2026 |
| Neon | Postgres free: 100 CU-h/mes, 1 GB, duerme tras 5 min y despierta en ms. Nunca borra datos por los límites |
| Supabase | Postgres free de 500 MB que **se pausa tras 1 semana sin uso** |

## Opciones consideradas

1. **Render (app) + Postgres de Render**: un solo proveedor, pero la base vence al mes.
2. **Railway (app + base)**: lo más cómodo y sin cold starts, pero pago.
3. **Render (app) + Supabase**: la pausa por inactividad corta el servicio en recesos y feriados largos.
4. **Render (app) + Neon (base)**: $0 sin vencimiento. Tiene cold starts en los dos servicios.

## Decisión

**Render** para el backend (Web Service free, Docker desde `backend/`, branch `main`, región Ohio) y **Neon** para Postgres (AWS `us-east-2`, conexión directa sin pooler). Los dos quedan en la misma zona. El detalle operativo está en [`docs/infraestructura.md`](../infraestructura.md).

## Consecuencias

- `BACKEND_URL` = `https://smartelevate.onrender.com`. Front y back quedan conectados a través del rewrite de Vercel, sin CORS.
- **Cold start** de ~1 min tras 15 min sin tráfico. Lo mitiga un workflow de keep-alive en horario de campus que le pega a `/api/ping`. Usamos ese endpoint, y no `/actuator/health`, para no despertar a Neon: con la base prendida 24/7 se superan las 100 CU-h del plan.
- Con 0.1 CPU y 512 MB, el arranque es lento y la memoria queda justa. Si el MVP lo necesita, el paso siguiente es Render Starter (USD 7/mes, sin sleep) sin cambiar nada más.
- El health check de Render es `/api/ping`. Un problema con la base igual se ve al deployar, porque Flyway corre al arrancar y la app no levanta si no llega a Postgres.
- El rollback de Render no revierte migraciones: se corrigen con una migración nueva ([ADR 0004](0004-migraciones-de-base-de-datos.md)).
- Desbloquea el storage de certificados (B-15). Las propuestas de email y storage están en [`docs/infraestructura.md`](../infraestructura.md#pendiente-email-y-storage).
