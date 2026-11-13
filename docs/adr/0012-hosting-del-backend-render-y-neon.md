# ADR-0012: Hosting del backend en Render y Postgres en Neon

## Metadata

- **Status:** Accepted
- **Date:** 2026-10-06
- **Deciders:** equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** [#5](https://github.com/Benji-9/SmartElevate/issues/5)
- **Related ADRs:** [ADR-0004](0004-migraciones-de-base-de-datos.md), [ADR-0013](0013-email-con-brevo-y-storage-en-r2.md)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0012-hosting-del-backend-render-y-neon.md`)

---

## 1. Context and Problem Statement

### Context

Sin un backend público, el front en Vercel no llega a la API (bloqueante B-01, [#5](https://github.com/Benji-9/SmartElevate/issues/5)). La imagen Docker ya estaba lista (B-08).

### Problem

¿Dónde alojamos el backend y la base Postgres para el MVP, sin costo y sin que la base venza durante la cursada?

---

## 2. Decision Drivers

- **Costo $0** para el MVP.
- Soporte de Docker y deploy desde GitHub.
- Un Postgres que no venza ni se borre durante la cursada.
- Spring Boot en la JVM necesita ~512 MB de RAM, lo que descarta las máquinas de 256 MB.

Planes gratuitos verificados el 2026-10-06:

| Opción | Plan gratuito |
| --- | --- |
| Render | Web Service free (0.1 CPU, 512 MB, 750 h/mes, duerme tras 15 min). Su Postgres free **vence a los 30 días** y después se borra |
| Railway | Sin plan gratis: USD 5 de crédito único y después USD 5/mes más uso |
| Fly.io | Sin plan gratis: solo un trial de 2 h o 7 días |
| Koyeb | Plan gratis cerrado a cuentas nuevas desde 02/2026 |
| Neon | Postgres free: 100 CU-h/mes, 1 GB, duerme tras 5 min y despierta en ms. Nunca borra datos por los límites |
| Supabase | Postgres free de 500 MB que **se pausa tras 1 semana sin uso** |

---

## 3. Considered Options

### Option A: Render (app) + Postgres de Render

**Pros:**
- Un solo proveedor.

**Cons:**
- La base vence al mes.

### Option B: Railway (app + base)

**Pros:**
- Lo más cómodo y sin cold starts.

**Cons:**
- Pago.

### Option C: Render (app) + Supabase

**Cons:**
- La pausa por inactividad corta el servicio en recesos y feriados largos.

### Option D: Render (app) + Neon (base)

**Pros:**
- $0 sin vencimiento.

**Cons:**
- Cold starts en los dos servicios.

---

## 4. Decision Outcome

**Chosen Option:** D, Render + Neon.

### Rationale

**Render** para el backend (Web Service free, Docker desde `backend/`, branch `main`, región Ohio) y **Neon** para Postgres (AWS `us-east-2`, conexión directa sin pooler). Los dos quedan en la misma zona. Es la única combinación gratuita que no vence ni se pausa. El detalle operativo está en [`docs/infraestructura.md`](../infraestructura.md).

---

## 5. Consequences

### Positive Consequences

- `BACKEND_URL` = `https://smartelevate.onrender.com`. Front y back quedan conectados a través del rewrite de Vercel, sin CORS.
- Desbloquea el storage de certificados (B-15). Email y storage se resolvieron en el [ADR-0013](0013-email-con-brevo-y-storage-en-r2.md).

### Negative Consequences

- **Cold start** de ~1 min tras 15 min sin tráfico.
- Con 0.1 CPU y 512 MB, el arranque es lento y la memoria queda justa. Si el MVP lo necesita, el paso siguiente es Render Starter (USD 7/mes, sin sleep) sin cambiar nada más.
- El rollback de Render no revierte migraciones: se corrigen con una migración nueva ([ADR-0004](0004-migraciones-de-base-de-datos.md)).

### Technical Implications

- Lo mitiga un workflow de keep-alive en horario de campus que le pega a `/api/ping`. Se usa ese endpoint, y no `/actuator/health`, para no despertar a Neon: con la base prendida 24/7 se superan las 100 CU-h del plan.
- El health check de Render es `/api/ping`. Un problema con la base igual se ve al deployar, porque Flyway corre al arrancar y la app no levanta si no llega a Postgres.

---

## 6. Implementation and Validation

- [x] Web Service en Render (Docker desde `backend/`, branch `main`, Ohio) y Postgres en Neon (`us-east-2`).
- [x] `BACKEND_URL` configurada y rewrite de Vercel.
- [x] Workflow de keep-alive sobre `/api/ping` en horario de campus.
- [ ] Evaluar Render Starter si el arranque o la memoria no alcanzan.

---

## 7. References

- [`docs/infraestructura.md`](../infraestructura.md)
- [ADR-0004](0004-migraciones-de-base-de-datos.md)
- [ADR-0013](0013-email-con-brevo-y-storage-en-r2.md)
- Issue [#5](https://github.com/Benji-9/SmartElevate/issues/5)
