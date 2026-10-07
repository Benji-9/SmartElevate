# Infraestructura y deploy

Qué servicios usamos, dónde se administran y cómo se opera la app en producción. Las decisiones están en los ADR [0003](adr/0003-deploy-frontend-vercel-via-github-actions.md) (frontend), [0012](adr/0012-hosting-del-backend-render-y-neon.md) (backend y base) y [0013](adr/0013-email-con-brevo-y-storage-en-r2.md) (email y storage).

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

El backend además usa:
  Brevo (SMTP)                    mails de verificación de cuenta
  Cloudflare R2 (bucket privado)  certificados de prioridad
```

El navegador solo habla con Vercel: `/api/*` lo reenvía Vercel al backend. Por eso no hace falta configurar CORS en producción.

## Servicios y accesos

| Servicio | Para qué | Dashboard | Titular |
| --- | --- | --- | --- |
| **Vercel** | Frontend (producción y previews) | [vercel.com](https://vercel.com) → team `takiprojects`, proyecto `smart-elevate` | @Benji-9 |
| **Render** | Backend | [dashboard.render.com](https://dashboard.render.com) → servicio `smartelevate` | @Benji-9 |
| **Neon** | Postgres de producción | [console.neon.tech](https://console.neon.tech) → proyecto `snowy-surf-03910636` | @Benji-9 |
| **Brevo** | Email transaccional (SMTP) | [app.brevo.com](https://app.brevo.com) → remitente `smartelevate.uade@gmail.com` | @Benji-9 |
| **Cloudflare** | Storage de certificados (R2) | [dash.cloudflare.com](https://dash.cloudflare.com) → R2 → bucket `smartelevate-certificados` | @Benji-9 |
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
| Brevo (Free) | 300 mails por día | Los envíos se rechazan hasta el día siguiente |
| Cloudflare R2 | 10 GB-mes de storage (clase Standard) · 1 M de escrituras y 10 M de lecturas por mes · egreso gratis | **Se cobra** a la tarjeta de la cuenta (USD 0,015 por GB-mes). Improbable: los certificados se borran al resolverse |

Opciones descartadas:

- **Postgres de Render:** el free **vence a los 30 días** y después lo borran.
- **Supabase:** pausa el proyecto tras 1 semana sin uso.
- **Koyeb, Fly.io y Railway:** ya no tienen plan gratuito.
- **Resend:** exige un dominio propio para mandar mails a terceros.

El detalle está en los ADR [0012](adr/0012-hosting-del-backend-render-y-neon.md) y [0013](adr/0013-email-con-brevo-y-storage-en-r2.md).

## Email con Brevo

- Envío por **SMTP** (`smtp-relay.brevo.com:587`, STARTTLS) con `spring-boot-starter-mail`. No usamos el SDK de Brevo: cambiar de proveedor es cambiar variables.
- Remitente verificado: `smartelevate.uade@gmail.com`. **No tenemos dominio propio**, así que Brevo reescribe el remitente a `smartelevate.uade@12377968.brevosend.com`. Es esperado y no se puede evitar sin dominio.
- Prueba del 2026-10-06: un mail a una casilla @uade.edu.ar **llegó a la bandeja de entrada**.
- **Si empiezan a caer en spam:** comprar un dominio (unos USD 10 por año, p. ej. en Cloudflare), autenticarlo en Brevo (**Senders, Domains & Dedicated IPs → Domains**: registros TXT, DKIM y DMARC) y cambiar `APP_MAIL_FROM`. No hace falta tocar código.

Variables en Render (Spring lee las `SPRING_MAIL_*` sin configuración extra):

| Variable | Valor |
| --- | --- |
| `SPRING_MAIL_HOST` | `smtp-relay.brevo.com` |
| `SPRING_MAIL_PORT` | `587` |
| `SPRING_MAIL_USERNAME` | Login SMTP de Brevo (`xxxx@smtp-brevo.com`, **no** el Gmail) |
| `SPRING_MAIL_PASSWORD` | SMTP key de Brevo |
| `APP_MAIL_FROM` | `smartelevate.uade@gmail.com` |

Al implementar el envío, hay que activar STARTTLS en la config (`spring.mail.properties.mail.smtp.starttls.enable: true` y `...starttls.required: true`): JavaMail no lo usa por defecto.

**Rotar la clave:** Brevo → **SMTP & API → SMTP → Generate a new SMTP key**, cargarla en Render y borrar la vieja.

## Storage de certificados en R2

Los certificados de prioridad son **datos de salud** ([ADR 0009](adr/0009-autenticacion-y-prioridad.md)): bucket privado, URLs firmadas cortas y borrado del archivo al resolver.

| Campo | Valor |
| --- | --- |
| Bucket | `smartelevate-certificados` |
| Ubicación | Location hint **Eastern North America (ENAM)**, cerca de Render |
| Storage class | Standard (el free tier no cubre *Infrequent Access*) |
| Acceso público | **Desactivado**: sin Public Development URL ni dominio propio. Sin credenciales responde `400` |
| Token de API | *Object Read & Write*, **solo** sobre este bucket |

Variables en Render:

| Variable | Valor |
| --- | --- |
| `APP_STORAGE_ENDPOINT` | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` (sin el bucket al final) |
| `APP_STORAGE_BUCKET` | `smartelevate-certificados` |
| `APP_STORAGE_ACCESS_KEY` | Access Key ID del token |
| `APP_STORAGE_SECRET_KEY` | Secret Access Key del token (se muestra una sola vez) |

R2 habla la API de S3: en el cliente se usa la región `auto`.

**Rotar las claves:** R2 → **Manage API tokens**. Crear un token nuevo con los mismos permisos, cargarlo en Render y recién después borrar el viejo.

> ⚠️ **No subir certificados reales** hasta resolver [B-13](bloqueantes.md) (Ley 25.326). R2 guarda los archivos en EE.UU., y la transferencia internacional de datos de salud es parte de lo que hay que validar.

## Probar los servicios desde tu máquina

Para usar las mismas credenciales en local, ponelas en un `.env` en la raíz del repo, con el formato `CLAVE=valor` y los mismos nombres que en Render. El archivo:

- Está en `.gitignore`.
- Claude Code no lo puede leer: hay una regla `deny` en `.claude/settings.json`.
- **Spring no lo carga solo:** para correr el backend con esas variables, exportalas antes (`set -a; . ./.env; set +a`).

El código que usa email y storage llega con la implementación de la autenticación ([B-05](bloqueantes.md)). Hasta entonces, estas variables están cargadas en Render pero nadie las lee.
