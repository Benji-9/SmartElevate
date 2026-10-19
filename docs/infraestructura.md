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
  Brevo (SMTP)                    mails de verificación de cuenta y de reset de contraseña
  Cloudflare R2 (bucket privado)  certificados de prioridad (todavía sin código que lo use, #123)
```

El navegador solo habla con Vercel: `/api/*` lo reenvía Vercel al backend, así la cookie del refresh es de primera parte. Aun así, `CORS_ALLOWED_ORIGINS` es obligatoria y tiene que traer el origen del front: Vercel reenvía el header `Origin`, y el backend rechaza con 403 (CORS y defensa CSRF del refresh) cualquier `Origin` que no esté en la lista.

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

### Checklist maestra de variables de producción

Es la única tabla con todo lo que hay que cargar en producción; el [README](../README.md#variables-de-entorno-del-backend) tiene la misma lista con la descripción de cada una para quien desarrolla. Verificada el 2026-10-16 contra `application*.yml` y las clases `@ConfigurationProperties`, y arrancando el jar con perfil `prod` (sin las cuatro de `RequiredProdProperties`, con `JWT_SECRET` corta, con mail activo sin `SPRING_MAIL_HOST` y sin `DATABASE_URL`).

- **Dónde:** *Render* = servicio `smartelevate` → **Environment** (cambiar una variable redeploya). *Vercel* = proyecto → **Settings → Environment Variables**. *GitHub* = repo → **Settings → Secrets and variables → Actions**.
- **Secretos** (clave de la base, JWT, HMAC del QR, SMTP): solo en el dashboard de Render. Se generan con `openssl rand -base64 48` (64 caracteres; el mínimo es 32 **bytes**). Corré el comando y pegá el **resultado**, nunca el texto del comando. `JWT_SECRET` y `APP_KIOSK_QR_SECRET` tienen que ser distintas y generadas, nunca copiadas de un ejemplo: `RequiredProdProperties` corta el arranque si son iguales, si contienen `openssl` o `rand -base64`, `change-me`/`changeme`/`secret`, un `<...>`, si son una sola letra repetida, solo minúsculas o tienen menos de 16 caracteres distintos (los valores nunca se loguean).
- **Duraciones** en ISO-8601 (`PT15M`, `P7D`). Un valor mal escrito en cualquier variable tipada (duración, número, zona horaria) hace que la app no arranque.

#### Obligatorias: sin ellas la app no arranca

| Variable | Define | Valor / cómo generarlo | Si falta |
| --- | --- | --- | --- |
| `DATABASE_URL` | Render | `jdbc:postgresql://<host de Neon>/neondb?sslmode=require` (conexión directa, ver [Neon](#base-de-datos-en-neon)) | No arranca: falla el datasource (`'url' must start with "jdbc"`) |
| `DATABASE_USER` | Render | `neondb_owner` | No arranca |
| `DATABASE_PASSWORD` | Render | Clave del rol en Neon (secreto) | No arranca |
| `JWT_SECRET` | Render | `openssl rand -base64 48` (secreto, mínimo 32 bytes) | No arranca: `app.security.jwt.secret must not be blank` (`RequiredProdProperties`); si es corta, `JWT_SECRET es demasiado corto` (`JwtConfig`); si es un ejemplo/baja entropía, `JWT_SECRET es un valor de ejemplo o de baja entropía ...`; si es igual a la del QR, `JWT_SECRET y APP_KIOSK_QR_SECRET no pueden ser iguales ...` |
| `APP_KIOSK_QR_SECRET` | Render | `openssl rand -base64 48` (secreto, mínimo 32 bytes, distinta de `JWT_SECRET`). Clave HMAC del QR ([ADR 0020](adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)). Rotarla invalida los QR en pantalla, que se renuevan solos en el ciclo siguiente | No arranca: `app.kiosk.qrSecret must not be blank`; si es corta, `APP_KIOSK_QR_SECRET es demasiado corto` (`QrTokenService`); si es un ejemplo/baja entropía, `APP_KIOSK_QR_SECRET es un valor de ejemplo o de baja entropía ...` |
| `APP_FRONTEND_URL` | Render | `https://smart-elevate.vercel.app`. Base de los links de los mails (verificación, reset) | No arranca: `app.verification.frontendUrl must not be blank` |
| `CORS_ALLOWED_ORIGINS` | Render | `https://smart-elevate.vercel.app`. Lista separada por comas, **exacta**: sin barra final, sin path y en minúsculas, tal como el navegador manda `Origin` (`https://smart-elevate.vercel.app/` y `https://Smart-Elevate.vercel.app` no matchean y dan 403 en login, refresh y logout) | No arranca: `app.cors.allowedOrigins must not be empty` |
| `SPRING_MAIL_HOST` | Render | `smtp-relay.brevo.com`. Obligatoria **mientras `APP_MAIL_ENABLED=true`** (el default en `prod`) | No arranca: no hay bean `JavaMailSender` para `SmtpEmailSender` |

Las cuatro que valida `RequiredProdProperties` (`JWT_SECRET`, `APP_KIOSK_QR_SECRET`, `APP_FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`) salen **todas juntas** en un solo mensaje `APPLICATION FAILED TO START` → `Binding validation errors`; en el log de Render está la lista de las que faltan. Los `DATABASE_*` fallan antes, al conectar.

#### Mail (Brevo)

| Variable | Define | Valor | Si falta |
| --- | --- | --- | --- |
| `APP_MAIL_ENABLED` | Render | Default `true` en `prod`. `false` no manda nada y deja el contenido del mail (con el link de verificación) en el log | Con el default, ver las `SPRING_MAIL_*` |
| `SPRING_MAIL_PORT` | Render | `2525` (STARTTLS, ya exigido en `application-prod.yml`). **No `587`**: Render free bloquea los puertos SMTP 25, 465 y 587 | Con `587` el envío da `Connect timed out` a los 5 s (queda en el log, la request responde bien): los mails no salen. Sin la variable Spring usa el 25 y falla igual |
| `SPRING_MAIL_USERNAME` | Render | Login SMTP de Brevo (`xxxx@smtp-brevo.com`, **no** el Gmail) | Arranca; el envío falla con error de autenticación |
| `SPRING_MAIL_PASSWORD` | Render | SMTP key de Brevo (secreto) | Ídem |
| `APP_MAIL_FROM` | Render | `smartelevate.uade@gmail.com`. Default `no-reply@localhost`, que Brevo rechaza | Arranca; Brevo rechaza el remitente |
| `APP_MAIL_SMTP_CONNECTION_TIMEOUT` / `APP_MAIL_SMTP_TIMEOUT` / `APP_MAIL_SMTP_WRITE_TIMEOUT` | Render | Milisegundos. Defaults `5000` / `10000` / `10000` (solo `prod`) | Se usan los defaults |

Un fallo del SMTP en runtime no tumba la request de registro: el error se registra en el log y el usuario puede pedir el reenvío.

#### Sesión, cookie y seguridad

| Variable | Define | Default | Notas |
| --- | --- | --- | --- |
| `JWT_ACCESS_TTL` | Render | `PT15M` | Vida del access token |
| `JWT_REFRESH_TTL` | Render | `P7D` | Vida del refresh y `Max-Age` de la cookie |
| `APP_REFRESH_COOKIE_SECURE` | Render | `true` | **No ponerla en `false` en producción.** Con `false` no arranca: `APP_REFRESH_COOKIE_SECURE no puede ser false en producción ...` (`RequiredProdProperties`). Mejor no cargarla: el default es `true` |
| `APP_REFRESH_COOKIE_NAME` / `_SAME_SITE` / `_PATH` | Render | `refresh_token` / `Lax` / `/api/auth` | No tocar: el front y el rewrite de Vercel asumen estos valores (ADR 0019, 0023) |
| `APP_LOGIN_THROTTLE_WINDOW` | Render | `PT15M` | Ventana deslizante del límite de logins fallidos |
| `APP_LOGIN_THROTTLE_MAX_FAILURES_PER_ACCOUNT` | Render | `10` | Entre 1 y 100 (NIST); fuera de rango, no arranca |
| `APP_LOGIN_THROTTLE_MAX_FAILURES_PER_IP` | Render | `100` | Mínimo 1. Depende de ver la IP real, ver [riesgos](#riesgos-del-primer-deploy) |
| `APP_VERIFICATION_TOKEN_TTL` / `APP_VERIFICATION_RESEND_COOLDOWN` | Render | `PT24H` / `PT1M` | Link de verificación de email |
| `APP_PASSWORD_RESET_TOKEN_TTL` / `APP_PASSWORD_RESET_REQUEST_COOLDOWN` | Render | `PT30M` / `PT1M` | Reset de contraseña |

#### Kioscos y QR

| Variable | Define | Default | Notas |
| --- | --- | --- | --- |
| `APP_KIOSK_SIMULATOR_ENABLED` | Render | `false` | **Tiene que quedar apagada (sin definir) en producción.** Con `true` un ADMIN puede crear kioscos simulados y sus check-ins se cuentan como simulados. Apagada, `POST /api/admin/kiosks` con `simulated: true` da 409 `SIMULATOR_DISABLED` |
| `APP_KIOSK_QR_TTL` | Render | `PT30S` | Vida de cada token del QR (se acepta también el del ciclo anterior). Reemplaza a `qr_rotation` de `app_config`, que `V15` borró |
| `APP_KIOSK_RATE_LIMIT_MAX_REQUESTS` / `APP_KIOSK_RATE_LIMIT_WINDOW` | Render | `10` / `PT1M` | Pedidos de token por kiosco; al pasarse, 429 con `Retry-After`. En memoria, por instancia |

#### Hora y jobs

| Variable | Define | Default | Notas |
| --- | --- | --- | --- |
| `APP_TIME_ZONE` | Render | `America/Argentina/Buenos_Aires` | Zona de las franjas y del horario. Render corre en UTC: no depende de la zona del servidor. No hace falta definirla |
| `APP_SCHEDULING_ENABLED` | Render | `true` | `false` apaga **todos** los `@Scheduled` (no-shows y recordatorios). Solo para tests; en producción dejarla |
| `APP_NO_SHOW_JOB_INTERVAL` | Render | `PT15S` | Cada cuánto se marcan los no-shows |
| `APP_REMINDER_JOB_ENABLED` | Render | `true` | `false` apaga solo los recordatorios de salida por mail (p. ej. si Brevo falla o se agota el cupo diario) |
| `APP_REMINDER_JOB_INTERVAL` | Render | `PT30S` | Cada cuánto corre el job de recordatorios |

#### Panel admin

| Variable | Define | Default | Notas |
| --- | --- | --- | --- |
| `APP_ADMIN_USER_SEARCH_MIN_QUERY_LENGTH` | Render | `3` | Mínimo 2 |
| `APP_ADMIN_USER_SEARCH_PAGE_SIZE` | Render | `20` | Entre 1 y 100 |

#### Plataforma

| Variable | Define | Default | Notas |
| --- | --- | --- | --- |
| `PORT` | Render | `8080` (en la imagen) | Lo inyecta Render (por defecto `10000`); Spring lo toma de `server.port` |
| `SPRING_PROFILES_ACTIVE` | Imagen | `prod` | Ya viene en el `Dockerfile`; no hace falta cargarla |
| `JAVA_OPTS` | Render | `-XX:MaxRAMPercentage=75` | Si Render reinicia por memoria (512 MB), `-XX:MaxRAMPercentage=60` |
| `APP_STORAGE_ENDPOINT` / `_BUCKET` / `_ACCESS_KEY` / `_SECRET_KEY` | Render | — | Reservadas para los certificados en R2 ([#123](https://github.com/Benji-9/SmartElevate/issues/123)): **el backend todavía no las lee**. `ENDPOINT` = `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` (sin el bucket), `BUCKET` = `smartelevate-certificados`; las claves son las del token de R2 (se ven una sola vez) |

#### Frontend y CI

| Variable | Define | Valor | Si falta |
| --- | --- | --- | --- |
| `BACKEND_URL` | GitHub (*Variables*; puede ser por environment `preview` / `production`) | `https://smartelevate.onrender.com`, sin barra final. El workflow la escribe en el rewrite de `vercel.json` | El workflow avisa con un warning y quita el rewrite: el front deploya **sin backend**. También la usa `keep-alive-backend.yml` (sin ella, el job se saltea) |
| `VITE_API_URL` | Vercel (*Production* y *Preview*) | `/api`. Con una URL absoluta la cookie del refresh sería cross-site y no se mantiene la sesión | Usa el default `/api` del cliente |
| `VERCEL_TOKEN`, `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID` | GitHub (*Secrets*) | Ver [README](../README.md#3-github-cargar-secrets-y-variables) | `deploy-frontend.yml` falla |

### Cómo arranca el contenedor

- **Imagen:** build multi-stage (`eclipse-temurin:25-jdk-noble` compila, `eclipse-temurin:25-jre-alpine` corre), usuario `spring` sin root. `ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]`: el `exec` deja a la JVM como PID 1, así recibe el `SIGTERM` de Render y cierra bien.
- **Puerto:** Render inyecta `PORT` y Spring escucha en `${PORT:8080}`. El `EXPOSE 8080` es solo informativo.
- **Memoria:** `-XX:MaxRAMPercentage=75` toma el 75 % del límite del contenedor (512 MB en free → ~384 MB de heap), y el resto queda para metaspace, hilos y el código nativo. Java 25 respeta los límites del contenedor, no hace falta `-Xmx`. Es justo: ante un `OOMKilled` en el log de Render, bajar a 60 con `JAVA_OPTS`.
- **Arranque:** con 0.1 CPU el arranque de la JVM más Flyway es lento (este documento estima ~1 min en un cold start); el primer deploy suma además el build de la imagen (~5–10 min). El health check de Render (`/api/ping`) responde cuando Tomcat está arriba, que es **después** de Flyway.
- **Base nueva:** Flyway crea todo desde `V1` en una base vacía de Neon (ver [riesgos](#riesgos-del-primer-deploy)).

### `render.yaml` (blueprint): propuesta, no agregado

Hoy el servicio está configurado a mano y funciona (ADR 0012). Se evaluó sumar un [`render.yaml`](https://render.com/docs/blueprint-spec) y **no se agrega**:

- Un blueprint es infraestructura como código para *crear* servicios. El servicio `smartelevate` ya existe; adoptarlo con un blueprint arriesga que Render lo trate como uno nuevo (otra URL, y habría que cambiar `BACKEND_URL`).
- El valor principal (reproducir el servicio) se cubre con la [tabla de configuración](#backend-en-render) y la checklist maestra de arriba. Las variables con `sync: false` igual habría que cargarlas a mano.
- Mantener dos fuentes de verdad (el dashboard y el archivo) es un costo para un equipo de 6 con un solo servicio.
- El health check del blueprint sería `/api/ping` (como decide el ADR 0012), no `/actuator/health`: `/actuator/health` consulta la base y mantendría despierto a Neon, que gasta cómputo del plan free.

Si el equipo lo quiere igual (por ejemplo, para recrear el servicio en otra cuenta), este es el esqueleto, sin ningún valor secreto:

```yaml
services:
  - type: web
    name: smartelevate
    runtime: docker
    dockerfilePath: ./Dockerfile
    dockerContext: .
    rootDir: backend
    branch: main
    region: ohio
    plan: free
    healthCheckPath: /api/ping
    autoDeployTrigger: checksPass
    envVars:
      - { key: SPRING_PROFILES_ACTIVE, value: prod }
      - { key: DATABASE_URL, sync: false }
      - { key: DATABASE_USER, sync: false }
      - { key: DATABASE_PASSWORD, sync: false }
      - { key: JWT_SECRET, sync: false }
      - { key: APP_KIOSK_QR_SECRET, sync: false }
      - { key: APP_FRONTEND_URL, sync: false }
      - { key: CORS_ALLOWED_ORIGINS, sync: false }
      - { key: SPRING_MAIL_HOST, value: smtp-relay.brevo.com }
      - { key: SPRING_MAIL_PORT, value: "2525" }
      - { key: SPRING_MAIL_USERNAME, sync: false }
      - { key: SPRING_MAIL_PASSWORD, sync: false }
      - { key: APP_MAIL_FROM, sync: false }
```

Los nombres de los campos no se probaron contra un workspace real de Render.

## Base de datos en Neon

- Proyecto `snowy-surf-03910636`, branch `production`, base `neondb`, rol `neondb_owner`, región **AWS US East 2 (Ohio)**.
- Usamos la conexión **directa** (el host no tiene `-pooler`). Hikari ya hace el pool, y Flyway y el lock pesimista del cupo andan mejor sin PgBouncer en el medio.
- Para pasar el connection string de Neon a JDBC: anteponer `jdbc:`, sacar `usuario:clave@` (van en sus propias variables) y sacar `&channel_binding=require`.
- El schema lo maneja **solo Flyway**. No crear ni modificar tablas a mano desde la consola de Neon: la app valida el schema al arrancar y no levantaría.

### Rotar la clave

1. Neon → **Branches → production → Roles → `neondb_owner` → Reset password**.
2. Pegar la nueva en Render → Environment → `DATABASE_PASSWORD` → **Save, rebuild and deploy**.

### Crear el primer ADMIN

No hay endpoint ni seed que cree administradores ([ADR 0009](adr/0009-autenticacion-y-prioridad.md)). La persona se registra y verifica el email; después se la promueve en el **SQL Editor de Neon** (en dev, en `/h2-console`):

```sql
UPDATE app_user SET role = 'ADMIN' WHERE email = 'nombre@uade.edu.ar';
```

La tabla es `app_user` y el rol vive en su columna `role` (`USER`, `REVIEWER`, `ADMIN`). Desde ahí, los demás roles se asignan con `PUT /api/admin/users/{id}/role`, que deja registro en `priority_audit`. Este `UPDATE` manual no queda auditado: es la única excepción. El rol se lee de la base en cada request, así que no hace falta que la persona vuelva a loguearse.

## Frontend en Vercel

Lo deploya [`deploy-frontend.yml`](../.github/workflows/deploy-frontend.yml) (ver [README](../README.md#cicd)). Lo que importa para el backend:

- La variable de GitHub **`BACKEND_URL`** = `https://smartelevate.onrender.com`. El workflow la escribe en el rewrite de `vercel.json` antes de buildear.
- Si cambia `BACKEND_URL`, el front recién la toma **en el próximo deploy de producción**.

### Headers de seguridad (CSP)

Los define `frontend/vercel.json` (sección `headers`, todas las rutas). Decisión y justificación: [ADR 0022](adr/0022-csp-y-headers-de-seguridad-en-vercel.md).

| Header | Valor | Por qué |
| --- | --- | --- |
| `Content-Security-Policy` | `default-src 'self'`, `script-src 'self'`, `style-src 'self'`, `style-src-attr 'unsafe-inline'`, `img-src 'self' data:`, `font-src 'self'`, `connect-src 'self'`, `media-src 'self' blob:`, `object-src 'none'`, `frame-ancestors 'none'`, `base-uri 'self'`, `form-action 'self'` | Mitiga XSS (el refresh vive en una cookie HttpOnly, ADR 0019) y clickjacking |
| `X-Content-Type-Options` | `nosniff` | Evita que el navegador adivine tipos MIME |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | No filtra rutas a otros orígenes |
| `Permissions-Policy` | `camera=(self)` y el resto denegado | El check-in escanea con la cámara |

HSTS lo agrega Vercel. Si se suma un origen externo (fuentes, analytics), hay que agregarlo a la CSP. Si #204 mantiene el proxy del mismo origen, `connect-src` queda en `'self'`.

**Checklist del preview de Vercel** (CSP, [ADR 0022](adr/0022-csp-y-headers-de-seguridad-en-vercel.md); sesión por cookie, [ADR 0019](adr/0019-refresh-token-en-cookie-httponly.md) y [0023](adr/0023-defensa-csrf-del-refresh-por-cookie.md)). Se corre una vez en el preview del PR de release (`develop` → `main`) y de nuevo en producción:

*CSP y headers*

- [ ] Consola sin `Refused to ...` en cada pantalla (login, Inicio, Reservar, Mis viajes, 404).
- [ ] Headers presentes en el documento, en un asset (`/assets/*.js`) y en una ruta profunda recargada.
- [ ] Inter y los logos se ven.
- [ ] Check-in con cámara, en un navegador sin `BarcodeDetector`, para que cargue el chunk de jsQR.
- [ ] Kiosco con el QR visible.
- [ ] Panel admin con las barras.

*Cookie del refresh a través del rewrite*

- [ ] `POST /api/auth/login` (DevTools → Network) responde 200 y su `Set-Cookie` es `refresh_token; Path=/api/auth; Secure; HttpOnly; SameSite=Lax`. En Application → Cookies, la cookie figura bajo el **dominio del front** (no `onrender.com`): es de primera parte. Si aparece "This Set-Cookie was blocked", el cliente no está usando `/api` (`VITE_API_URL`).
- [ ] **Recargar la pestaña mantiene la sesión**: la primera request es `POST /api/auth/refresh` con 200 y cookie nueva, sin volver al login.
- [ ] Sin sesión (ventana privada, o después de cerrar sesión), abrir el front hace un `POST /api/auth/refresh` que responde **401**: es lo esperado, no un error. Ese 401 trae un `Set-Cookie` que expira la cookie. Cualquier otro código es un problema: **403** = falta el header `X-Requested-With` o el `Origin` no está en `CORS_ALLOWED_ORIGINS`.
- [ ] Dos pestañas abiertas a la vez: ninguna cierra la sesión de la otra (el canje corre dentro de `navigator.locks`).
- [ ] Cerrar sesión: `POST /api/auth/logout` responde 204 y la cookie desaparece.

*CORS exacto*

- [ ] `CORS_ALLOWED_ORIGINS` coincide con el `Origin` que manda el navegador, carácter por carácter (`https://smart-elevate.vercel.app`: sin barra final y en minúsculas). Para verificarlo sin navegador: `curl -s -o /dev/null -w '%{http_code}' -X POST https://smartelevate.onrender.com/api/auth/refresh -H 'X-Requested-With: SmartElevate' -H 'Origin: https://smart-elevate.vercel.app'` → `401` (pasó el control de origen; no hay cookie). Con otro `Origin` da `403`.
- [ ] **Las previews tienen una URL propia** (`smart-elevate-git-<rama>-takiprojects.vercel.app`, o un hash por deploy) que **no** está en `CORS_ALLOWED_ORIGINS`: ahí el login y el refresh dan 403 por diseño (el login, `Invalid CORS request`; el refresh, la defensa CSRF), mientras que lo que no manda `Origin` (los GET) anda. Para probar la sesión en una preview concreta hay que sumar su origen exacto a la lista en Render (sin comodines), probar y quitarlo. Es una consecuencia deducida del código y de que Vercel reenvía `Origin`; **todavía no se probó en una preview real**.
- [ ] Una llamada a `/api` con 200 (por ejemplo `GET /api/elevators`).

Notas:

- La Vercel Toolbar y los comentarios de preview inyectan scripts de `vercel.live`: solo en previews vas a ver `Refused to load script`. Ignorarlo; no agregar `vercel.live` a la CSP.
- `style-src-attr` lo soportan Safari 15.4+ y los navegadores actuales (aceptable para el MVP).
- Las previews no tienen Deployment Protection ([B-07](bloqueantes.md)): no cargar datos reales.

## Cómo llega un cambio a producción

1. PR a `develop` → CI. El front se deploya como preview y el backend no se deploya.
2. Release `develop` → `main`:
   - **Front:** `CI Frontend` termina OK y `deploy-frontend.yml` deploya a producción.
   - **Back:** `backend-verify` termina OK y Render buildea la imagen nueva (la primera vez tarda ~5–10 min) y la publica cuando `/api/ping` responde.

### Smoke test del deploy

Después de cada deploy del backend, y siempre después del primero. Hay un script que hace la secuencia de abajo, [`scripts/smoke-test.sh`](../scripts/smoke-test.sh) (solo `curl` y `jq`), y los comandos a mano para cuando algo falla y hay que mirar de cerca.

**Antes de empezar** (Render → **Environment**): todas las [obligatorias](#obligatorias-sin-ellas-la-app-no-arranca) cargadas y ninguna en blanco. Si falta una, el deploy queda en *failed* y el log de Render dice cuál (`APPLICATION FAILED TO START`). El primer arranque corre `V1`..`V21` sobre la base vacía de Neon; en el log: `Successfully applied 21 migrations`.

#### Con el script

```bash
# 1. Solo salud (sin cuentas)
BASE_URL=https://smartelevate.onrender.com scripts/smoke-test.sh

# 2. Registrar la cuenta de prueba (manda el mail de verificación) y verificarla desde el mail
BASE_URL=https://smartelevate.onrender.com SMOKE_REGISTER=1 \
  SMOKE_EMAIL=smoke@uade.edu.ar SMOKE_PASSWORD='<clave larga>' scripts/smoke-test.sh

# 3. Promover a un ADMIN (ver "Crear el primer ADMIN") y correr todo
BASE_URL=https://smartelevate.onrender.com SMOKE_ORIGIN=https://smart-elevate.vercel.app \
  SMOKE_EMAIL=smoke@uade.edu.ar SMOKE_PASSWORD='<clave>' \
  ADMIN_EMAIL=admin@uade.edu.ar ADMIN_PASSWORD='<clave>' scripts/smoke-test.sh
```

- Las credenciales van por variables de entorno del shell: **no las pongas en el repo ni en el historial compartido**.
- `BASE_URL` puede ser Render directo o el front (`https://smart-elevate.vercel.app`, que pasa por el rewrite). Contra Render directo hay que pasar `SMOKE_ORIGIN` con el valor de `CORS_ALLOWED_ORIGINS`, para que el refresh y el logout se prueben como los hace el navegador.
- `SMOKE_CHECKIN=1` en vez de cancelar la reserva, espera la ventana de check-in y la usa. Tarda hasta ~30 minutos (la reserva abre 30 min antes de la salida y cierra 2 min antes), así que se usa una vez por deploy importante. `EXPECT_SIMULATOR=1` solo para probar un entorno con el simulador habilitado.
- Termina con código 1 en el primer paso que falla, mostrando la respuesta. Los pasos sin datos (sin salidas reservables fuera del horario de operación) se saltean con `SKIP` y no cuentan como falla.
- El primer request tras 15 min sin tráfico puede tardar ~1 min (Render dormido); el script espera hasta 120 s por request.

#### Los pasos a mano

`B=https://smartelevate.onrender.com`. Las respuestas esperadas son las del script.

1. **Salud.** `curl -s $B/api/ping` → `{"status":"ok"}`. `curl -s $B/actuator/health` → `{"status":"UP"}` (despierta a Neon: si da `DOWN`, mirar `DATABASE_*`).
2. **Registro.** `curl -s -o /dev/null -w '%{http_code}\n' -X POST $B/api/auth/register -H 'Content-Type: application/json' -d '{"fullName":"Smoke Test","email":"smoke@uade.edu.ar","legajo":"1234567","password":"<clave larga>","declaredUserType":"STUDENT"}'` → `201`. Con `APP_MAIL_ENABLED=true` llega el mail desde Brevo con un link `$APP_FRONTEND_URL/verificar-email?token=...`. Con `false` el link está en el log de Render (`Mail NO enviado`). Verificar: `curl -X POST $B/api/auth/verify -H 'Content-Type: application/json' -d '{"token":"<token>"}'` → `204`. Un login antes de verificar da `403`.
3. **Login y cookie.** `curl -si -X POST $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"smoke@uade.edu.ar","password":"<clave>"}'` → `200`, el body trae `accessToken`, y el header:
   `Set-Cookie: refresh_token=<valor>; Path=/api/auth; Max-Age=604800; Expires=...; Secure; HttpOnly; SameSite=Lax`. Si falta `Secure`, `APP_REFRESH_COOKIE_SECURE` está en `false`: corregir antes de seguir.
4. **Refresh.** Sin `X-Requested-With: SmartElevate` → `403`. Con el header, sin cookie → `401`. Con el header y `-H 'Cookie: refresh_token=<valor>'` (y `-H 'Origin: https://smart-elevate.vercel.app'`) → `200` y una cookie **nueva**; la anterior queda inutilizable (reusarla revoca la sesión entera).
5. **Perfil y salidas.** `curl -s $B/api/users/me -H "Authorization: Bearer $T"` → `200` con `role: USER`. `curl -s $B/api/departures -H "Authorization: Bearer $T"` → `200`, una lista de salidas con `id`, `elevator`, `departureAt`, `remainingSeats`. Vacía fuera del horario de operación o de la ventana de 30 min.
6. **Reservar.** Elegir una salida y un piso válido (`curl -s $B/api/elevators` lista los `floors` de cada ascensor; los pisos bajos, de 1 a `low_floors_threshold` de `app_config`, son solo para movilidad reducida; el script usa el piso más alto). `POST /api/reservations` con `{"departureId":<id>,"originFloor":0,"destinationFloor":<piso>}` → `201`. Una segunda reserva activa da `409 ACTIVE_LIMIT_REACHED`.
7. **Cancelar.** `DELETE /api/reservations/<id>` → `204`. Más de 10 min antes de la salida queda `CANCELLED`; después, `CANCELLED_LATE` (cuenta como falta).
8. **Primer ADMIN.** Ver [Crear el primer ADMIN](#crear-el-primer-admin) (`UPDATE app_user SET role = 'ADMIN' ...` en el SQL Editor de Neon). No hace falta volver a loguearse.
9. **Panel admin.** Con el token del ADMIN: `GET /api/admin/kpis?period=TODAY` → `200`. `GET /api/admin/kiosks` → `200` con `"simulatorEnabled": false`. Con un usuario común, los dos dan `403`.
10. **Simulador apagado.** `POST /api/admin/kiosks` con `{"elevatorId":"01","simulated":true}` → `409` con `code: SIMULATOR_DISABLED`. Si da `201`, `APP_KIOSK_SIMULATOR_ENABLED` está en `true` en Render: apagarlo.
11. **Kiosco real y QR.** `POST /api/admin/kiosks` con `{"elevatorId":"01","simulated":false}` → `201` con `id` y `secret` (**única vez** que se ve). `curl -s $B/api/kiosks/me/qr-token -H "Authorization: Bearer <secret>"` → `200` con `token`, `expiresAt` (~30 s) y `elevatorId`. Sin credencial o con una revocada → `401 KIOSK_UNAUTHORIZED`. Si no se va a usar, revocarlo: `POST /api/admin/kiosks/<id>/revoke` → `204`.
12. **Check-in.** Con una reserva activa y dentro de la ventana (de 1 min antes a 2 min después de la salida), `POST /api/check-ins` con `{"token":"<token del QR>"}` y el Bearer del usuario → `201` con `outcome: ON_TIME` (u `OTHER_ELEVATOR`) y `source: REAL`. Fuera de la ventana, `409 OUTSIDE_CHECK_IN_WINDOW`; sin reserva, `409 NO_ACTIVE_RESERVATION`.
13. **Logout.** `POST /api/auth/logout` con el header CSRF y la cookie → `204` y un `Set-Cookie` que la expira.

Desde el navegador, en producción: login en https://smart-elevate.vercel.app, recargar la pestaña (la sesión se mantiene) y confirmar que el mail de verificación trae un link a `APP_FRONTEND_URL`.

#### Qué se probó de verdad (2026-10-16)

El script (que cubre los pasos 1 a 13, salvo el SQL del ADMIN, que se hizo con el shell de H2) se ejecutó contra el jar real con **perfil `prod`** (Secure activo, `server.forward-headers-strategy: native`, 21 migraciones aplicadas), pero con H2 en lugar de Neon y el mail apagado. No se probó contra Render, Neon, Vercel ni Brevo. Queda para un humano con acceso: el cold start, el SMTP real de Brevo, TLS hacia Neon y la IP real detrás del proxy de Render.

### Riesgos del primer deploy

Lo que puede salir mal la primera vez y cómo se ve. Ninguno está probado contra Render ni Neon.

1. **Migraciones sobre una base nueva de Neon.** Flyway corre `V1`..`V21` al arrancar, antes de que Tomcat acepte conexiones, así que el health check de Render tarda en pasar. Las 21 migraciones se aplicaron sin error en H2 (`MODE=PostgreSQL`) con el jar de `prod`, y los tests con Testcontainers las corren contra `postgres:18-alpine` en el CI. Si una falla en Neon, la app no levanta y el deploy queda *failed*: leer el log de Flyway, **no** editar la migración (ya mergeada, ADR 0004) ni crear tablas a mano; corregir con una `V22`. Una migración que falla a la mitad puede dejar la base a medias: borrar el branch `production` de Neon y recrearlo es válido solo mientras no haya datos reales.
2. **`semester_end_dates` provisorio.** `V18` lo carga con `2026-12-31`, sin confirmar con el calendario de UADE ([B-19](bloqueantes.md)). Sin un cierre desde hoy, **aprobar docentes falla**. Cargar el calendario real antes del 31/12/2026: `UPDATE app_config SET config_value = '2026-12-31,2027-07-31' WHERE config_key = 'semester_end_dates';`. La app avisa en el log (WARN) al arrancar si no queda ninguno.
3. **IP real y el límite de logins por IP.** `server.forward-headers-strategy: native` hace que Tomcat lea `X-Forwarded-For`, pero solo si el proxy que lo manda es uno **interno** (rangos privados y loopback por defecto). Si el proxy de Render no entra en esos rangos, `getRemoteAddr()` devuelve la IP del proxy y el tope por IP (`APP_LOGIN_THROTTLE_MAX_FAILURES_PER_IP`, 100) lo comparte todo el campus; con el rewrite de Vercel de por medio, además, la IP es la de Vercel. El límite por cuenta (10) no depende de esto. Las IP se guardan hasheadas, así que no se ven en la base. Cómo probarlo: bajar temporalmente `APP_LOGIN_THROTTLE_MAX_FAILURES_PER_IP` a 3, fallar logins con emails distintos desde dos redes distintas y ver si el 429 aparece en ambas a la vez (IP compartida) o solo en una.
4. **Scheduler.** En `prod` el pool de `@Scheduled` tiene **2 hilos** (`spring.task.scheduling.pool.size`): un mail lento del recordatorio no frena el job de no-shows. Con 0.1 CPU, si el SMTP se cuelga, los timeouts de `APP_MAIL_SMTP_*` (5 s / 10 s) acotan cuánto. Si Brevo falla de forma sostenida: `APP_REMINDER_JOB_ENABLED=false`.
5. **Instancia única.** El límite de pedidos de token de los kioscos está **en memoria por instancia** (`// ponytail:` en `KioskService`); con N instancias el tope real sería N veces el configurado, y un reinicio lo reinicia. El límite de logins sí vive en la base (tabla de fallos con hashes), aunque contar e insertar no es atómico (`// ponytail:` en `LoginThrottle`: dos fallos simultáneos pueden pasar el tope por uno o dos). Con una sola instancia en Render alcanza; **no subir el servicio a más de una** sin mover el de kioscos a la base o a un cache compartido.
6. **Mails desde Brevo sin probar en producción.** El envío funcionó en una prueba manual (2026-10-06) pero no desde el backend desplegado. Sin dominio propio, el remitente sale reescrito a `@brevosend.com` y puede caer en spam. Si el SMTP falla, el registro igual se crea (el error queda en el log): el usuario no puede verificar su cuenta hasta que el reenvío funcione. Plan B para no bloquear a nadie: leer el link del log con `APP_MAIL_ENABLED=false`, o promover la verificación a mano en la base.
7. **`qr_rotation` ya no está en `app_config`.** `V15` lo borró; la vida del QR es `APP_KIOSK_QR_TTL` (default `PT30S`). Si alguien busca la fila `qr_rotation` en Neon y no la encuentra, es lo esperado.
8. **Swagger UI y `/v3/api-docs` son públicos** también en producción (decisión del README: documentación abierta); no exponen datos, pero listan todos los endpoints, incluidos los de admin. `/h2-console` no existe en `prod` (404).
9. **Memoria y cold start** del plan free (512 MB, 0.1 CPU): ver [Cómo arranca el contenedor](#cómo-arranca-el-contenedor) y [Keep-alive](#keep-alive).

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

- Envío por **SMTP** (`smtp-relay.brevo.com:2525`, STARTTLS) con `spring-boot-starter-mail`. No usamos el SDK de Brevo: cambiar de proveedor es cambiar variables.
- Remitente verificado: `smartelevate.uade@gmail.com`. **No tenemos dominio propio**, así que Brevo reescribe el remitente a `smartelevate.uade@12377968.brevosend.com`. Es esperado y no se puede evitar sin dominio.
- **Puerto 2525, no 587.** Render free bloquea las conexiones salientes a los puertos 25, 465 y 587; con 587 el log muestra `Couldn't connect to host, port: smtp-relay.brevo.com, 587; timeout 5000` y no sale ningún mail (verificación ni reset). Brevo acepta STARTTLS en el 2525 y funciona (verificado el 2026-10-19). Si algún día se pasa a un plan pago, 587 vuelve a ser una opción.
- Prueba del 2026-10-06: un mail a una casilla @uade.edu.ar **llegó a la bandeja de entrada**.
- **Si empiezan a caer en spam:** comprar un dominio (unos USD 10 por año, p. ej. en Cloudflare), autenticarlo en Brevo (**Senders, Domains & Dedicated IPs → Domains**: registros TXT, DKIM y DMARC) y cambiar `APP_MAIL_FROM`. No hace falta tocar código.

Las variables (`SPRING_MAIL_*`, `APP_MAIL_FROM`, `APP_MAIL_ENABLED`) están en la [checklist maestra](#mail-brevo); Spring lee las `SPRING_MAIL_*` sin configuración extra.

STARTTLS ya está activado en `application-prod.yml`. `APP_FRONTEND_URL` (obligatoria, ver [Backend en Render](#backend-en-render)) arma los links de verificación y de reset de contraseña. En `prod` el envío real está activo por defecto (`APP_MAIL_ENABLED=true`); en dev y tests solo se loguea.

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

Las variables `APP_STORAGE_*` (reservadas para la implementación futura, [#123](https://github.com/Benji-9/SmartElevate/issues/123); hoy el backend no las lee) están en la [checklist maestra](#plataforma).

R2 habla la API de S3: en el cliente se usa la región `auto`.

**Rotar las claves:** R2 → **Manage API tokens**. Crear un token nuevo con los mismos permisos, cargarlo en Render y recién después borrar el viejo.

> ⚠️ **No subir certificados reales** hasta resolver [B-13](bloqueantes.md) (Ley 25.326). R2 guarda los archivos en EE.UU., y la transferencia internacional de datos de salud es parte de lo que hay que validar.

## Probar los servicios desde tu máquina

Para usar las mismas credenciales en local, ponelas en un `.env` en la raíz del repo, con el formato `CLAVE=valor` y los mismos nombres que en Render. El archivo:

- Está en `.gitignore`.
- Claude Code no lo puede leer: hay una regla `deny` en `.claude/settings.json`.
- **Spring no lo carga solo:** para correr el backend con esas variables, exportalas antes (`set -a; . ./.env; set +a`).

El email ya está en uso (verificación y reset). Las `APP_STORAGE_*` están cargadas en Render pero nadie las lee hasta que lleguen los certificados ([#123](https://github.com/Benji-9/SmartElevate/issues/123)).
