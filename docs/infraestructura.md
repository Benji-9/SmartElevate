# Infraestructura y deploy

Qué servicios usamos, dónde se administran y cómo se opera la app en producción. Las decisiones están en los ADR [0003](adr/0003-deploy-frontend-vercel-via-github-actions.md) (frontend) y [0012](adr/0012-hosting-del-backend-render-y-neon.md) (backend y base).

> **Nunca** se escriben claves en este archivo ni en el repo. Viven en el dashboard de cada servicio y en los secrets de GitHub.

## Mapa

```
Navegador
   │
   ▼
smart-elevate.vercel.app          Vercel: SPA + rewrite /api/* → backend
   │  /api/*
   ▼
smartelevate.onrender.com         Render: imagen Docker de backend/ (Spring Boot, perfil prod)
   │  JDBC + TLS
   ▼
Neon Postgres (AWS us-east-2)     Flyway crea y migra el schema al arrancar
```

El navegador solo habla con Vercel: `/api/*` lo reenvía Vercel al backend. Por eso no hace falta configurar CORS en producción.

## Servicios y accesos

| Servicio | Para qué | Dashboard | Titular |
| --- | --- | --- | --- |
| **Vercel** | Frontend (producción y previews) | [vercel.com](https://vercel.com) → team `takiprojects`, proyecto `smart-elevate` | @Benji-9 |
| **Render** | Backend | [dashboard.render.com](https://dashboard.render.com) → servicio `smartelevate` | @Benji-9 |
| **Neon** | Postgres de producción | [console.neon.tech](https://console.neon.tech) → proyecto `snowy-surf-03910636` | @Benji-9 |
| **GitHub Actions** | CI, deploy del front, keep-alive | [Actions](https://github.com/Benji-9/SmartElevate/actions) | Repo |

Para entrar a un dashboard, pedile acceso al titular. No compartan las claves por chat: el titular las carga directamente en el servicio que las usa.

### URLs

| Qué | URL |
| --- | --- |
| App (producción) | https://smart-elevate.vercel.app |
| API (directo a Render) | https://smartelevate.onrender.com/api/ping |
| API (a través de Vercel) | https://smart-elevate.vercel.app/api/ping |
| Health (incluye la base) | https://smartelevate.onrender.com/actuator/health |
| Swagger UI | https://smartelevate.onrender.com/swagger-ui.html |

## Backend en Render

Configuración del Web Service:

| Campo | Valor | Por qué |
| --- | --- | --- |
| Language | Docker | Usa [`backend/Dockerfile`](../backend/Dockerfile) (perfil `prod`, usuario no root) |
| Branch | `main` | Producción sale de `main`, igual que el front |
| Region | Ohio (US East) | La misma zona que Neon (`us-east-2`). Render no tiene región en Sudamérica |
| Root Directory | `backend/` | Un cambio solo en `frontend/` no redeploya el backend |
| Instance type | Free (0.1 CPU, 512 MB) | |
| Health Check Path | `/api/ping` | No toca la base: `/actuator/health` despertaría a Neon en cada chequeo |
| Auto-Deploy | After CI Checks Pass | No deploya si `backend-verify` falla |
| Docker Command / Pre-Deploy | vacíos | El `ENTRYPOINT` arranca la app y Flyway migra al iniciar |

Variables de entorno (en Render → **Environment**):

| Variable | Valor |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://<host de Neon>/neondb?sslmode=require` |
| `DATABASE_USER` | `neondb_owner` |
| `DATABASE_PASSWORD` | La clave del rol en Neon |

`PORT` lo define Render y `SPRING_PROFILES_ACTIVE=prod` ya viene en la imagen. Si aparecen reinicios por falta de memoria, agregar `JAVA_OPTS=-XX:MaxRAMPercentage=60`. La lista completa de variables está en el [README](../README.md#variables-de-entorno-del-backend).

## Base de datos en Neon

- Proyecto `snowy-surf-03910636`, branch `production`, base `neondb`, rol `neondb_owner`, región **AWS US East 2 (Ohio)**.
- Usamos la conexión **directa** (el host no tiene `-pooler`). Hikari ya hace el pool, y Flyway y el lock pesimista del cupo andan mejor sin PgBouncer en el medio.
- Para pasar el connection string de Neon a JDBC: anteponer `jdbc:`, sacar `usuario:clave@` (van en sus propias variables) y sacar `&channel_binding=require`.
- El schema lo maneja **solo Flyway**. No crear ni modificar tablas a mano desde la consola de Neon: la app valida el schema al arrancar y no levantaría.

### Rotar la clave

1. Neon → **Branches → production → Roles → `neondb_owner` → Reset password**.
2. Pegar la nueva en Render → Environment → `DATABASE_PASSWORD` → **Save, rebuild and deploy**.

## Frontend en Vercel

Lo deploya [`deploy-frontend.yml`](../.github/workflows/deploy-frontend.yml) (ver [README](../README.md#cicd)). Lo que importa para el backend:

- La variable de GitHub **`BACKEND_URL`** = `https://smartelevate.onrender.com`. El workflow la escribe en el rewrite de `vercel.json` antes de buildear.
- Si cambia `BACKEND_URL`, el front recién la toma **en el próximo deploy de producción**.

## Cómo llega un cambio a producción

1. PR a `develop` → CI. El front se deploya como preview y el backend no se deploya.
2. Release `develop` → `main`:
   - **Front:** `CI Frontend` termina OK y `deploy-frontend.yml` deploya a producción.
   - **Back:** `backend-verify` termina OK y Render buildea la imagen nueva (la primera vez tarda ~5–10 min) y la publica cuando `/api/ping` responde.

### Redeployar sin cambios de código

- **Backend:** Render → **Manual Deploy → Deploy latest commit**. Para solo reiniciar: **Restart service**.
- **Frontend a producción:** relanzar el último run de **CI Frontend** con evento `push` en `main` (Actions → CI Frontend → filtrar por `main` → *Re-run all jobs*).
  - Relanzar un run de **Deploy Frontend** no siempre sirve. En la lista todos figuran con branch `main` porque `workflow_run` corre desde la rama por defecto, pero si el CI que lo disparó era de `develop` o de un PR, el resultado es una preview. En el log, `IS_PRODUCTION: true` confirma que fue a producción.

### Volver a una versión anterior

- **Backend:** Render → **Events** → en un deploy anterior, **Rollback**. Si el deploy problemático incluía una migración, el rollback no la revierte: hay que corregirla con una migración nueva.
- **Frontend:** Vercel → **Deployments** → en un deploy anterior, **Promote to Production**.

## Keep-alive

El plan free de Render duerme el servicio tras **15 min sin tráfico**, y el primer request después de eso tarda ~1 min más el arranque de la JVM. El proxy de Vercel puede cortar ese request por timeout.

[`keep-alive-backend.yml`](../.github/workflows/keep-alive-backend.yml) le pega a `/api/ping` cada 10 min, de **07:00 a 22:59 (hora de Buenos Aires)**. Fuera de ese horario el servicio puede dormir.

- Le pega a `/api/ping` y no a `/actuator/health` porque el ping no toca la base. Neon puede seguir durmiendo y no se consume su cuota de cómputo (ver límites).
- Las 750 h mensuales gratis de Render alcanzan aunque el servicio esté prendido todo el mes (744 h).
- GitHub solo ejecuta los `schedule` desde `main` y puede atrasarlos unos minutos si hay mucha carga. En repos públicos, además, **desactiva los workflows programados tras 60 días sin actividad** en el repo: si pasa, se reactiva desde Actions.
- Se puede correr a mano desde Actions → **Keep-alive Backend** → *Run workflow*.

## Límites de los planes gratuitos

Verificados el 2026-10-06.

| Servicio | Límite | Qué pasa al superarlo |
| --- | --- | --- |
| Render (Free) | 750 h de instancia por mes por workspace · duerme tras 15 min sin tráfico · 512 MB RAM | Sin horas: el servicio queda suspendido hasta el mes siguiente |
| Neon (Free) | 100 CU-h de cómputo y 1 GB de storage por proyecto por mes · duerme tras 5 min sin uso | Sin cómputo: la base se suspende hasta el mes siguiente. **No se borran datos** |
| Vercel (Hobby) | Uso no comercial | — |

Opciones descartadas:

- **Postgres de Render:** el free **vence a los 30 días** y después lo borran.
- **Supabase:** pausa el proyecto tras 1 semana sin uso.
- **Koyeb, Fly.io y Railway:** ya no tienen plan gratuito.

El detalle está en el [ADR 0012](adr/0012-hosting-del-backend-render-y-neon.md).

## Pendiente: email y storage

Hacen falta cuando se implemente la autenticación ([ADR 0009](adr/0009-autenticacion-y-prioridad.md)). Son **propuestas**: el equipo todavía tiene que aprobarlas.

| Para qué | Propuesta | Por qué | Issue |
| --- | --- | --- | --- |
| Email de verificación de cuentas @uade.edu.ar | **Brevo** por SMTP (300 mails/día gratis) | Acepta como remitente un mail verificado. No tenemos dominio propio, y **Resend** lo exige para mandar a terceros | [#25](https://github.com/Benji-9/SmartElevate/issues/25) (B-14) |
| Certificados de prioridad (datos de salud) | **Cloudflare R2** (10 GB gratis, API de S3, URLs firmadas) | Bucket privado, URLs firmadas cortas y sin pausa por inactividad. Pide cargar un medio de pago para activarse | [#26](https://github.com/Benji-9/SmartElevate/issues/26) (B-15) |

- **Email:** usar `spring-boot-starter-mail` con SMTP genérico (`MAIL_HOST`, `MAIL_USER`, `MAIL_PASSWORD`) y no el SDK de un proveedor. Así cambiar de proveedor es cambiar variables.
- **Riesgo de spam:** si los mails a @uade.edu.ar caen en spam, la salida es un dominio propio (unos USD 10 por año).
