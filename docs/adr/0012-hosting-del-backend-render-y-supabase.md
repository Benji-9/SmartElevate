# ADR-0012: Hosting del backend en Render y Postgres en Supabase

## Metadata

- **Status:** Accepted
- **Date:** 2026-10-06
- **Deciders:** equipo SmartElevate
- **Owner:** @Benji-9
- **Related Issues:** [#5](https://github.com/Benji-9/SmartElevate/issues/5)
- **Related ADRs:** [ADR-0004](0004-migraciones-de-base-de-datos.md), [ADR-0013](0013-email-con-brevo-y-storage-en-r2.md)
- **Nota:** reescrito al formato MADR el 2026-11-13. El 2026-11-25 la base pasó a Supabase: la cuota de cómputo por horas del proveedor anterior se agotó a mitad de mes (ver *Decision Drivers*).

---

## 1. Context and Problem Statement

### Context

Sin un backend público, el front en Vercel no llega a la API (bloqueante B-01, [#5](https://github.com/Benji-9/SmartElevate/issues/5)). La imagen Docker ya estaba lista (B-08).

### Problem

¿Dónde alojamos el backend y la base Postgres para el MVP, sin costo y sin que la base se agote o se borre durante la cursada?

---

## 2. Decision Drivers

- **Costo $0** para el MVP.
- Soporte de Docker y deploy desde GitHub.
- Una base que no venza ni se borre, y cuya cuota no dependa de cuántas horas está activa: los jobs `@Scheduled` (no-shows cada 15 s, recordatorios cada 30 s) la consultan todo el tiempo que el backend está despierto.
- Spring Boot en la JVM necesita ~512 MB de RAM, lo que descarta las máquinas de 256 MB.

Planes gratuitos (verificados el 2026-10-06; Supabase, el 2026-11-25 contra [supabase.com/pricing](https://supabase.com/pricing) y su documentación):

| Opción | Plan gratuito |
| --- | --- |
| Render | Web Service free (0.1 CPU, 512 MB, 750 h/mes, duerme tras 15 min). Su Postgres free **vence a los 30 días** y después se borra |
| Railway | Sin plan gratis: USD 5 de crédito único y después USD 5/mes más uso |
| Fly.io | Sin plan gratis: solo un trial de 2 h o 7 días |
| Koyeb | Plan gratis cerrado a cuentas nuevas desde 02/2026 |
| Supabase | Postgres free: instancia Nano (CPU compartida, 60 conexiones), 500 MB por proyecto, 2 proyectos activos. No documenta una cuota de horas de cómputo. **Se pausa tras 1 semana de baja actividad** y se puede restaurar hasta 1 año después |
| Postgres serverless con cuota de CU-h/mes | Se agota si algo mantiene la base activa: con los jobs cada 15 s no llegó a fin de mes |

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

### Option C: Render (app) + Postgres serverless con cuota de cómputo

**Pros:**
- $0 y la base se duerme sola cuando no hay conexiones.

**Cons:**
- Los jobs `@Scheduled` impiden que se duerma mientras Render está despierto, y la cuota mensual se agotó antes de fin de mes.

### Option D: Render (app) + Supabase (base)

**Pros:**
- $0 y sin cuota de horas de cómputo documentada: los jobs constantes no la consumen.
- Los mismos jobs cuentan como actividad y evitan la pausa.

**Cons:**
- Pausa por inactividad tras 1 semana sin uso.
- 500 MB de storage.

---

## 4. Decision Outcome

**Chosen Option:** D, Render + Supabase.

### Rationale

**Render** para el backend (Web Service free, Docker desde `backend/`, branch `main`) y **Supabase** para Postgres (AWS `us-west-2`, **Session pooler**: la conexión directa de Supabase es solo IPv6 y Render free es IPv4). La Data API de Supabase queda desactivada: el backend entra solo por JDBC. El detalle operativo está en [`docs/infraestructura.md`](../infraestructura.md).

---

## 5. Consequences

### Positive Consequences

- `BACKEND_URL` = `https://smartelevate.onrender.com`. Front y back quedan conectados a través del rewrite de Vercel, sin CORS.
- Desbloquea el storage de certificados (B-15). Email y storage se resolvieron en el [ADR-0013](0013-email-con-brevo-y-storage-en-r2.md).
- Los jobs `@Scheduled` pueden mantener su frecuencia (15 s y 30 s) sin gastar cuota.

### Negative Consequences

- **Cold start** de ~1 min tras 15 min sin tráfico.
- Con 0.1 CPU y 512 MB, el arranque es lento y la memoria queda justa. Si el MVP lo necesita, el paso siguiente es Render Starter (USD 7/mes, sin sleep) sin cambiar nada más.
- **Pausa de Supabase** tras 1 semana sin actividad (recesos largos con el keep-alive apagado): se restaura a mano desde el dashboard (hasta 1 año después de la pausa).
- Render corre en Ohio y la base en Oregón: ~50 ms de ida y vuelta por consulta.
- El rollback de Render no revierte migraciones: se corrigen con una migración nueva ([ADR-0004](0004-migraciones-de-base-de-datos.md)).

### Technical Implications

- Lo mitiga un workflow de keep-alive en horario de campus que le pega a `/api/ping`. Se usa ese endpoint, y no `/actuator/health`, para que el health check de Render no dependa de la base.
- El health check de Render es `/api/ping`. Un problema con la base igual se ve al deployar, porque Flyway corre al arrancar y la app no levanta si no llega a Postgres.
- Hay que usar el Session pooler (puerto 5432). El Transaction pooler (6543) no soporta prepared statements ni los advisory locks de sesión que usa Flyway.

---

## 6. Implementation and Validation

- [x] Web Service en Render (Docker desde `backend/`, branch `main`) y Postgres en Supabase (`us-west-2`, Session pooler).
- [x] `BACKEND_URL` configurada y rewrite de Vercel.
- [x] Workflow de keep-alive sobre `/api/ping` en horario de campus.
- [ ] Confirmar en los logs de Render que Flyway aplica todas las migraciones sobre Supabase.
- [ ] Evaluar Render Starter si el arranque o la memoria no alcanzan.

---

## 7. References

- [`docs/infraestructura.md`](../infraestructura.md)
- [ADR-0004](0004-migraciones-de-base-de-datos.md)
- [ADR-0013](0013-email-con-brevo-y-storage-en-r2.md)
- Issue [#5](https://github.com/Benji-9/SmartElevate/issues/5)
