# Usuarios, autenticación y prioridad

> Estado: **aprobado** ([#6](https://github.com/Benji-9/SmartElevate/issues/6)). Decisión de diseño en [ADR 0009](../adr/0009-autenticacion-y-prioridad.md).

## Registro

- Solo emails **@uade.edu.ar**, **verificados por link** antes del primer login (por SMTP con Brevo: [ADR 0013](../adr/0013-email-con-brevo-y-storage-en-r2.md)).
- **Email y legajo únicos.**
- No hay acceso al sistema de UADE: el **legajo es autodeclarado**. La verificación real es la del email institucional.
- El email se guarda en **minúsculas**: `Ana@UADE.edu.ar` y `ana@uade.edu.ar` son la misma cuenta. El legajo son solo dígitos, hasta 10.
- La cuenta nace **común y sin verificar**. El pedido de registro (`POST /api/auth/register`) no tiene campo de rol ni de prioridad, y si el JSON trae alguno se ignora.
- Si el email o el legajo ya tienen cuenta, el registro responde **409** e indica cuál chocó (la pantalla marca ese campo). El login no va a revelar si un email existe.

### Contraseña

Alineada a NIST SP 800-63B en [#211](https://github.com/Benji-9/SmartElevate/issues/211) ([ADR 0015](../adr/0015-politica-de-contrasenas-nist-800-63b.md), con la tabla de qué cumple y qué no). Reemplaza el tope en bytes de [#181](https://github.com/Benji-9/SmartElevate/issues/181). El registro y la "contraseña nueva" del reset ([#138](https://github.com/Benji-9/SmartElevate/issues/138)) aplican **la misma política**.

- **Entre 8 y 64 caracteres.** Se cuentan **caracteres** (code points), no bytes: una "ñ", una tilde o un emoji valen 1. NIST pide 15 de mínimo cuando la contraseña es el único factor; elegimos 8 por usabilidad y lo compensamos con la lista de comunes, Argon2id y el límite de intentos.
- Antes de validar y de hashear (registro, reset y login) se **normaliza con NFKC**: la "ñ" compuesta y la descompuesta son la misma contraseña.
- **Sin reglas de composición** (no se exigen mayúsculas, números ni símbolos), **sin cambio periódico obligatorio**, y se puede **pegar** y usar **gestores de contraseñas**.
- Se rechazan las **2500 contraseñas más comunes** (lista embebida de SecLists más variantes locales como "contraseña", "uade2026" o "ascensor"), sin distinguir mayúsculas: "Esa contraseña es muy común y fácil de adivinar. Elegí otra."
- Se guarda como **hash Argon2id**. Los hashes BCrypt de antes de #211 siguen sirviendo y se pasan a Argon2id la próxima vez que la persona entra. La contraseña nunca se guarda ni se loguea en texto plano, ni siquiera con el log en DEBUG.

### Límite de intentos de login

- Por **cuenta** y por **IP**, en una **ventana deslizante**: con **10 fallos de la cuenta** o **100 de la IP** en los últimos **15 minutos**, el login responde **429** con `Retry-After` (sin mirar la contraseña) hasta que el fallo más viejo sale de la ventana. El tope por cuenta no puede pasar de 100 (NIST). Es un límite **por ventana más un tope por IP**, no la lectura literal de NIST de "100 seguidos"; el tope por IP es conservador porque la red de la universidad sale por pocas IPs (NAT) y se calibra en el piloto ([ADR 0015](../adr/0015-politica-de-contrasenas-nist-800-63b.md)). Los tres valores viven en `application.yml` (`app.login-throttle.*`), con override por `APP_LOGIN_THROTTLE_WINDOW`, `APP_LOGIN_THROTTLE_MAX_FAILURES_PER_ACCOUNT` y `APP_LOGIN_THROTTLE_MAX_FAILURES_PER_IP` ([#225](https://github.com/Benji-9/SmartElevate/issues/225), [ADR 0017](../adr/0017-donde-viven-los-parametros.md)).
- El bloqueo siempre es **temporal**: un tercero no puede dejar bloqueada una cuenta para siempre. Un **login correcto** o un **reset de contraseña** borran los fallos de la cuenta.
- Cuenta igual un email que no existe: el 429 no revela qué cuentas hay. Se guarda el hash de la cuenta y de la IP, nunca el email ni la IP.

### Olvidé mi contraseña

Implementado en [#138](https://github.com/Benji-9/SmartElevate/issues/138).

- `POST /api/auth/password/forgot` responde **202 siempre**, exista o no la cuenta (y también si hubo un pedido reciente: la espera entre pedidos, 1 minuto, se aplica en silencio porque un 429 delataría qué cuentas existen).
- El link vale **30 minutos**, se usa **una sola vez** y se guarda hasheado. Pedir otro invalida el anterior. Vencimiento y espera son configuración (`app.password-reset.*`).
- **Cuenta sin verificar que pide reset**: no se manda link de reset; se le **reenvía el link de verificación** (con su propia espera). Primero tiene que verificar el email.
- `POST /api/auth/password/reset` aplica la política de contraseña de arriba, guarda el hash nuevo y **revoca todos los refresh tokens** del usuario. Los access tokens ya emitidos siguen valiendo hasta que vencen (15 min).

### Tipo de usuario declarado

Decidido en [#32](https://github.com/Benji-9/SmartElevate/issues/32).

- En el registro, la persona elige **Estudiante** o **Docente** (`declaredUserType`: `STUDENT` / `TEACHER`, `STUDENT` por defecto). No hay opción "Personal".
- Es **declarativo**: se guarda como dato, pero **no otorga rol ni prioridad**. Todo usuario nace común.
- Si declara **Docente**, queda en la cola **"Docentes a validar"** del panel de administración ([#140](https://github.com/Benji-9/SmartElevate/issues/140)):
  - Si el ADMIN aprueba, obtiene la prioridad `TEACHER`.
  - Si rechaza, sigue como usuario común.
- La prioridad se sigue evaluando **solo en el servidor al reservar**, nunca a partir de este campo.
- **Cambiarlo después** ([#262](https://github.com/Benji-9/SmartElevate/issues/262)): quien ya tenía cuenta como estudiante y pasa a ser docente o ayudante no se registra de nuevo. Un ADMIN cambia el tipo desde la búsqueda de usuarios del panel (`PUT /api/admin/users/{id}/declared-type`), en los dos sentidos:
  - **Exige el email verificado**, igual que la cola. Si no lo verificó, 409.
  - **Estudiante → docente:** recibe la prioridad `TEACHER` **directamente**, sin pasar por la cola "Docentes a validar": lo decide un ADMIN, así que no hace falta una segunda aprobación. Vale hasta el fin del cuatrimestre en curso, igual que al aprobar, y se renueva igual (al vencer vuelve a la cola). Si antes lo habían rechazado, también la recibe. Si no hay un cierre de cuatrimestre cargado desde hoy, el cambio falla con 409 y no se modifica nada.
  - **Docente → estudiante:** sale de la cola y, si tenía la prioridad de docente vigente, **se le revoca en el mismo paso**. La prioridad de movilidad reducida no se toca.
  - Cada cambio queda en `priority_audit` (`DECLARED_TYPE_CHANGED`, con `STUDENT->TEACHER` o `TEACHER->STUDENT`); el otorgamiento queda como `TEACHER_APPROVED` y la revocación como `PRIORITY_REVOKED`.

## Roles y prioridad

- El **rol y la prioridad nunca vienen en el body del registro**: todo usuario nace común.
- Los asigna un **ADMIN** desde el panel de administración.
- **Docentes**: el ADMIN aprueba a los que se declararon docentes ([ver arriba](#tipo-de-usuario-declarado)).
- **Movilidad reducida**: la marca viene de OFALU ([ver abajo](#movilidad-reducida)).
- **Vigencia de la prioridad de docente:** hasta el **fin del cuatrimestre en curso**, renovable (decidido el 14/10). Los cierres de cuatrimestre van en `app_config` (`semester_end_dates`); al aprobar, la prioridad vence al terminar el día del primer cierre que sea hoy o después (hora de Buenos Aires). Al vencer, el docente vuelve a la cola "Docentes a validar" y aprobarlo de nuevo lo renueva hasta el cierre siguiente. Si no hay un cierre cargado desde hoy, la aprobación falla: hay que cargar el próximo ([#218](https://github.com/Benji-9/SmartElevate/issues/218)).

Modelo implementado (roles en `V5`, prioridad y auditoría en `V10`, [#121](https://github.com/Benji-9/SmartElevate/issues/121)):

| Concepto | Valores |
| --- | --- |
| Rol | `USER`, `REVIEWER` (sin función hoy, se evalúa quitarlo: [ADR 0024](../adr/0024-movilidad-reducida-la-marca-viene-de-ofalu.md)), `ADMIN` |
| Categoría de prioridad | `NONE`, `TEACHER`, `REDUCED_MOBILITY` |
| Estado de la solicitud de prioridad | `PENDING`, `APPROVED`, `REJECTED`, con fecha de vencimiento opcional (`expires_at`) |

### Primer ADMIN

No hay endpoint, seed ni migración que cree administradores: así no queda ningún dato del admin en el repo, que es público.

1. La persona se **registra normalmente** con su email @uade.edu.ar y lo **verifica**.
2. Quien administra la base la promueve con un `UPDATE` en el **SQL Editor de Neon**. En dev, lo mismo en `/h2-console`. La sentencia exacta está en [`infraestructura.md`](../infraestructura.md#crear-el-primer-admin).
3. Desde ahí, los demás `ADMIN` y `REVIEWER` los asigna un ADMIN desde el panel.

Render free no permite SSH ni comandos sueltos, así que no hay un comando para correr dentro del servidor.

## Movilidad reducida

Decidido el 16/10 ([ADR 0024](../adr/0024-movilidad-reducida-la-marca-viene-de-ofalu.md)).

- La marca la pone **UADE, a través de OFALU** (Oficina de Alumnos), que recibe el aviso de la persona (temporal o permanente) y maneja los certificados.
- SmartElevate **no sube, guarda, revisa ni muestra certificados** y no trata datos de salud. El perfil solo informa que hay que avisar en OFALU.
- A futuro habrá una integración con OFALU para recibir la marca. Formato, vigencia y temporal vs permanente: a definir con OFALU.
- Hoy el backend conserva la categoría `REDUCED_MOBILITY` y el ascensor dedicado IND1 06, pero **ningún endpoint asigna esa categoría**: nadie queda marcado en la app hasta la integración.
- Pregunta abierta: si un `ADMIN` puede marcarla a mano mientras tanto. **No está decidido ni implementado.**

## Sesión (JWT)

- **Access token corto (15 min) más refresh token (7 días)**, configurables con `JWT_ACCESS_TTL` y `JWT_REFRESH_TTL`.
- Implementado en [#120](https://github.com/Benji-9/SmartElevate/issues/120): el JWT lleva solo `sub` (id de usuario); el rol se lee de la base en cada request. El refresh es opaco, de un solo uso y se guarda hasheado; reusar uno ya rotado revoca toda la sesión. Credenciales erróneas dan 401 sin distinguir si el email existe, y una cuenta sin verificar no puede loguearse (403, solo tras acertar la clave).
- Decidido el 14/10 ([#204](https://github.com/Benji-9/SmartElevate/issues/204), [ADR 0019](../adr/0019-refresh-token-en-cookie-httponly.md)): el refresh pasa a una cookie `HttpOnly; Secure; SameSite=Lax`, a través del proxy del mismo origen de `vercel.json`. Implementado: login y refresh la setean (`Path=/api/auth`, `Max-Age` = TTL del refresh) y ya no devuelven el refresh en el body; `/auth/refresh` y `/auth/logout` la leen sin body y exigen el header `X-Requested-With: SmartElevate` y un `Origin` permitido ([ADR 0023](../adr/0023-defensa-csrf-del-refresh-por-cookie.md)). Reemplaza al [ADR 0014](../adr/0014-refresh-token-en-sessionstorage.md).
- La **prioridad se evalúa en el servidor al reservar**, consultando el estado actual. **No se usa un claim del token**: la prioridad puede vencer o revocarse mientras el token sigue vigente.
