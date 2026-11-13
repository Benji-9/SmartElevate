# Variables de entorno

Referencia de las variables del backend. Las del frontend (`VITE_*`) están en el [README del frontend](../../frontend/README.md#variables-de-entorno). Reglas: los secretos van solo en variables de entorno y los parámetros técnicos en `application.yml` con override por variable ([ADR 0017](../adr/0017-donde-viven-los-parametros.md)). Fuente de verdad de los defaults: `backend/src/main/resources/application*.yml`.

En producción las carga el equipo en Render; la checklist completa (quién la define, cómo generar cada secreto, qué pasa si falta) está en [`docs/infraestructura.md`](../infraestructura.md#checklist-maestra-de-variables-de-producción).

| Variable | Default | Descripción |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` (H2) o `prod` (Postgres) |
| `PORT` | `8080` | Puerto HTTP |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` solo en dev | Orígenes permitidos, separados por coma y **exactos**: sin barra final y en minúsculas (`https://smart-elevate.vercel.app`). **Obligatorio en `prod`** (sin default: la app no arranca sin él) |
| `APP_TIME_ZONE` | `America/Argentina/Buenos_Aires` | Zona del campus para franjas y horario. No depende de la zona del servidor (Render corre en UTC) |
| `JWT_SECRET` | — | Secreto de firma de los access tokens (HS256), mínimo 32 caracteres. **Obligatorio en `prod`** (la app no arranca sin él); en dev, si falta, se usa una clave efímera. Generalo con `openssl rand -base64 48`: corré el comando y pegá el **resultado**, nunca el comando. Distinto de `APP_KIOSK_QR_SECRET`; un ejemplo, un placeholder o un valor de baja entropía impide el arranque. Nunca en el repo |
| `JWT_ACCESS_TTL` | `PT15M` | Vida del access token (duración ISO-8601) |
| `JWT_REFRESH_TTL` | `P7D` | Vida del refresh token (duración ISO-8601); también el `Max-Age` de su cookie |
| `APP_REFRESH_COOKIE_SECURE` | `true` (`false` en dev) | Atributo `Secure` de la cookie del refresh; solo se apaga para `http://localhost`. `false` solo vale en dev: en `prod` con `false` la app no arranca |
| `APP_REFRESH_COOKIE_NAME` / `_SAME_SITE` / `_PATH` | `refresh_token` / `Lax` / `/api/auth` | Nombre y atributos de la cookie del refresh (ADR 0019, 0023) |
| `APP_LOGIN_THROTTLE_WINDOW` | `PT15M` | Ventana deslizante del límite de logins fallidos (duración ISO-8601) |
| `APP_LOGIN_THROTTLE_MAX_FAILURES_PER_ACCOUNT` | `10` | Logins fallidos por cuenta dentro de la ventana; entre 1 y 100 (NIST), si no la app no arranca |
| `APP_LOGIN_THROTTLE_MAX_FAILURES_PER_IP` | `100` | Logins fallidos por IP dentro de la ventana |
| `APP_VERIFICATION_TOKEN_TTL` | `PT24H` | Vida del link de verificación de email |
| `APP_VERIFICATION_RESEND_COOLDOWN` | `PT1M` | Espera mínima entre reenvíos del mail de verificación |
| `APP_PASSWORD_RESET_TOKEN_TTL` | `PT30M` | Vida del link de reset de contraseña |
| `APP_PASSWORD_RESET_REQUEST_COOLDOWN` | `PT1M` | Espera mínima entre pedidos de reset de contraseña |
| `DATABASE_URL` | — | Solo `prod`. Formato JDBC: `jdbc:postgresql://host:5432/db` |
| `DATABASE_USER` | — | Solo `prod` |
| `DATABASE_PASSWORD` | — | Solo `prod` |
| `APP_MAIL_ENABLED` | `false` en dev, `true` en `prod` | `true` manda los mails por SMTP; `false` solo los loguea (el link de verificación aparece en el log) |
| `APP_MAIL_FROM` | `no-reply@localhost` | Remitente de los mails |
| `APP_MAIL_ASYNC_THREADS` | `2` | Mínimo 1 (si no, la app no arranca). Hilos del executor de los mails masivos (aviso de ascensor fuera de servicio, #134) |
| `APP_MAIL_ASYNC_QUEUE` | `50` | Mínimo 0. Cola de ese executor. Si se llena, el envío corre en el hilo que lo pidió (no se pierden avisos) |
| `APP_MAIL_SMTP_CONNECTION_TIMEOUT` | `5000` | Solo `prod`. Milisegundos para conectar al SMTP |
| `APP_MAIL_SMTP_TIMEOUT` | `10000` | Solo `prod`. Milisegundos de espera de lectura del SMTP |
| `APP_MAIL_SMTP_WRITE_TIMEOUT` | `10000` | Solo `prod`. Milisegundos de espera de escritura al SMTP |
| `SPRING_MAIL_HOST` | — | **Obligatoria con el mail activo** (default de `prod`): sin ella la app no arranca. Brevo: `smtp-relay.brevo.com` |
| `SPRING_MAIL_PORT` | — | Brevo: `2525` (STARTTLS). No usar `587` en Render free: bloquea los puertos SMTP salientes (25, 465, 587) y el envío da `Connect timed out` |
| `SPRING_MAIL_USERNAME` | — | Login SMTP de Brevo |
| `SPRING_MAIL_PASSWORD` | — | SMTP key de Brevo (secreto: solo en el servicio, nunca en el repo) |
| `APP_FRONTEND_URL` | `http://localhost:5173` solo en dev | **Obligatorio en `prod`** (sin default: la app no arranca sin él). Base de los links de los mails: verificación (`<url>/verificar-email?token=…`) y reset de contraseña (`<url>/recuperar/nueva?token=…`) |
| `APP_KIOSK_QR_SECRET` | — | Clave HMAC-SHA256 del token del QR ([ADR 0020](../adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)), mínimo 32 caracteres y distinta de `JWT_SECRET`. **Obligatoria en `prod`** (la app no arranca sin ella); en dev, si falta, se usa una efímera. Generala con `openssl rand -base64 48` (el **resultado**, nunca el comando) y que no sea un ejemplo ni baja entropía: si lo es, no arranca. Rotarla invalida los QR en pantalla, que se renuevan solos en el ciclo siguiente. Nunca en el repo |
| `APP_KIOSK_QR_TTL` | `PT30S` | Vida de cada token del QR (segundos enteros); se acepta también el del ciclo anterior. Reemplaza a `qr_rotation` de `app_config` |
| `APP_KIOSK_SIMULATOR_ENABLED` | `false` | `true` permite crear kioscos simulados (simulador de admin); sus check-ins cuentan como simulados. **Dejarlo apagado en prod** |
| `APP_KIOSK_RATE_LIMIT_MAX_REQUESTS` | `10` | Pedidos de token por kiosco dentro de la ventana (se espera ~1 cada 30 s); al pasarse, 429 con `Retry-After` |
| `APP_KIOSK_RATE_LIMIT_WINDOW` | `PT1M` | Ventana del límite de pedidos por kiosco |
| `APP_ADMIN_USER_SEARCH_MIN_QUERY_LENGTH` / `_PAGE_SIZE` | `3` / `20` | Buscador de usuarios del panel admin: mínimo de caracteres (≥ 2) y tamaño de página (1 a 100) |
| `APP_SCHEDULING_ENABLED` | `true` | `false` apaga todos los jobs `@Scheduled` (no-shows y recordatorios); lo usan los tests |
| `APP_NO_SHOW_JOB_INTERVAL` | `PT15S` | Cada cuánto se marcan los no-shows |
| `APP_REMINDER_JOB_ENABLED` / `_INTERVAL` | `true` / `PT30S` | Recordatorio de salida por mail; `false` lo desactiva (p. ej. sin SMTP) |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75` | Solo en la imagen Docker. Si Render reinicia por memoria, `-XX:MaxRAMPercentage=60` |

**Checklist de `prod`.** Sin estas variables (o con alguna en blanco) la app no arranca:

- [ ] `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`
- [ ] `JWT_SECRET` (mínimo 32 caracteres; generada con `openssl rand -base64 48`, nunca copiada de un ejemplo)
- [ ] `APP_FRONTEND_URL` (p. ej. `https://smart-elevate.vercel.app`)
- [ ] `CORS_ALLOWED_ORIGINS` (al menos el dominio del front)
- [ ] `APP_KIOSK_QR_SECRET` (mínimo 32 caracteres, **distinta** de `JWT_SECRET` y generada, nunca copiada de un ejemplo; clave HMAC del QR, #229)

- [ ] `SPRING_MAIL_HOST` (con el mail activo, el default en `prod`; más `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` y `APP_MAIL_FROM` para que los mails salgan)

La app no arranca sin `SPRING_MAIL_HOST` si el mail está activo; sin las demás `SPRING_MAIL_*` arranca pero los mails fallan. `JWT_SECRET`, `APP_FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` y `APP_KIOSK_QR_SECRET` se validan al arrancar en `RequiredProdProperties` (#222), que además rechaza las dos claves si son iguales, si son un ejemplo/placeholder (p. ej. el texto `openssl rand -base64 48`) o de baja entropía, y `APP_REFRESH_COOKIE_SECURE=false`. Después de deployar, correr [`scripts/smoke-test.sh`](../../scripts/smoke-test.sh) ([guía](../infraestructura.md#smoke-test-del-deploy)).
