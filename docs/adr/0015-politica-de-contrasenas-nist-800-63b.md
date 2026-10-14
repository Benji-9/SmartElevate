# 0015. Adecuación de la política de contraseñas a NIST SP 800-63B

- **Estado:** Aceptado (2026-10-14)
- **Fecha:** 2026-10-14
- **Issues:** [#211](https://github.com/Benji-9/SmartElevate/issues/211). Reemplaza el criterio de bytes de [#181](https://github.com/Benji-9/SmartElevate/issues/181).
- **Autores:** @Benji-9

## Contexto

Con [#181](https://github.com/Benji-9/SmartElevate/issues/181) la contraseña tenía entre 8 y 72 caracteres y, además, **hasta 72 bytes**, porque BCrypt no acepta más. Una "ñ" ocupa 2 bytes en UTF-8, así que 36 "ñ" ya no entraban: el límite dependía del idioma de la persona. [NIST SP 800-63B rev. 4](https://pages.nist.gov/800-63-4/sp800-63b.html) (§3.1.1) pide lo contrario: cada carácter Unicode cuenta como uno, se normaliza antes de comparar y se aceptan al menos 64.

Además, el login no tenía límite de intentos y no había lista de contraseñas prohibidas: con un mínimo de 8 y sin otra defensa, adivinar contraseñas por fuerza bruta en línea quedaba abierto.

Restricciones: es un MVP universitario, sin presupuesto para servicios externos (p. ej. la API de Have I Been Pwned) y con un solo factor (no hay MFA). Corre en una sola instancia de Render.

## Opciones consideradas

1. **Seguir con BCrypt y 72 bytes**: no cambia nada, pero el tope en bytes contradice NIST y castiga al español.
2. **BCrypt con prehash (SHA-256 antes de BCrypt)**: saca el tope de 72 bytes, pero es una construcción propia, con trampas conocidas (bytes nulos, "password shucking") y menos clara que cambiar de algoritmo.
3. **Argon2id**, sin tope de bytes, con migración gradual de los hashes BCrypt: es la recomendación actual de OWASP y Spring Security lo trae.

## Decisión

Opción **3**, junto con el resto de los controles de NIST, con los matices de abajo:

- **Largo**: entre **8 y 64 caracteres**, contados como **code points** (`codePointCount` en Java, `[...p].length` en el front), no como bytes ni como unidades UTF-16 (un emoji vale 1).
- **Normalización NFKC** antes de validar y de hashear, en el registro, el reset y el login: la "ñ" compuesta y la descompuesta, o las letras de ancho completo, dan la misma contraseña.
- **Argon2id** con los parámetros mínimos de OWASP (19 MiB, 2 pasadas, 1 hilo, sal de 16 bytes, hash de 32) vía `Argon2PasswordEncoder` (implementación de BouncyCastle `bcprov-jdk18on`). Un `DelegatingPasswordEncoder` guarda el algoritmo como prefijo: la migración V13 le pone `{bcrypt}` a los hashes existentes y el login exitoso los vuelve a hashear con `{argon2}`. El hash falso con el que se compara cuando el email no existe (para que tarde lo mismo) también es Argon2id.
- **Lista de 2500 contraseñas comunes**, embebida en `backend/src/main/resources/security/common-passwords.txt` y comparada sin distinguir mayúsculas tras NFKC. Fuente: [SecLists](https://github.com/danielmiessler/SecLists) (licencia MIT, © 2018 Daniel Miessler), commit `997dd861b9fa2623f06f30d5f9085713ce830f0c`, archivos `Spanish_Pwdb_common-password-list-top-150.txt` y `xato-net-10-million-passwords-100000.txt`, en ese orden y por frecuencia. Solo se tomaron las de 8 a 64 caracteres (las más cortas ya las rechaza el largo). Las primeras 54 son variantes locales del equipo: "contraseña", "uade2026", "ascensor", "smartelevate", clubes, "buenosaires", etc. El rechazo dice "Esa contraseña es muy común y fácil de adivinar. Elegí otra."
- **Límite de logins fallidos** por cuenta y por IP, en una **ventana deslizante**, con parámetros en `app_config`: `login_failure_window` (PT15M), `login_max_failures_per_account` (10, validado entre 1 y 100) y `login_max_failures_per_ip` (100). Con el tope alcanzado se responde **429 con `Retry-After`** sin mirar la contraseña, hasta que el fallo más viejo sale de la ventana. La cuenta se identifica por el email normalizado, exista o no, para que el 429 no revele qué emails tienen cuenta. Los fallos se guardan en la base (tabla `login_failure`, con el SHA-256 de la cuenta o de la IP, nunca el email ni la IP) y se podan al salir de la ventana. Un login correcto o un reset de contraseña borran los fallos de la cuenta; los de la IP, no.
- Se mantiene: **sin reglas de composición**, **sin cambio periódico obligatorio**, se puede **pegar** y usar **gestores de contraseñas**, y se puede ver la contraseña al escribirla.

### NIST SP 800-63B frente a SmartElevate

| Requisito de NIST (rev. 4, §3.1.1 y §3.2.2) | SmartElevate | Estado |
| --- | --- | --- |
| Mínimo de 15 caracteres si la contraseña es el único factor (8 si hay MFA) | Mínimo 8, sin MFA | **Matiz** |
| Aceptar al menos 64 caracteres | Máximo 64 | Cumple |
| Aceptar todo carácter imprimible ASCII, el espacio y Unicode; cada code point cuenta como uno | Sin restricción de caracteres; se cuentan code points | Cumple |
| Normalizar Unicode antes de hashear (NFKC o NFKD) | NFKC en registro, reset y login | Cumple |
| Sin reglas de composición | No se exigen mayúsculas, números ni símbolos | Cumple |
| Sin cambio periódico obligatorio | No hay vencimiento de contraseña | Cumple |
| Comparar contra una lista de contraseñas comunes, esperables o filtradas | Lista embebida de 2500 | Cumple (con lista acotada) |
| Permitir pegar y gestores de contraseñas | Se permite | Cumple |
| Ofrecer ver la contraseña mientras se escribe | Botón "Mostrar contraseña" | Cumple |
| Sin pistas ni preguntas de seguridad | No hay | Cumple |
| Hash con sal y función de derivación costosa | Argon2id (OWASP) | Cumple |
| Limitar los fallos seguidos por cuenta a 100 como máximo | 10 por ventana de 15 min (configurable hasta 100) | **Matiz** |
| Disuadir el bloqueo de la cuenta por terceros | Bloqueo temporal (ventana deslizante) y el reset lo levanta | Cumple |
| Ignorar los fallos previos tras un login correcto | Se borran los de la cuenta | Cumple |
| Ofrecer un segundo factor | No hay | **Pendiente** |

### Matices y por qué

- **Mínimo 8 y no 15.** El público son estudiantes que entran desde el celular varias veces por día, para reservar un ascensor. 15 caracteres es una barrera fuerte para un MVP sin datos de alto valor (lo más sensible, los certificados, lo ve solo un REVIEWER). Lo compensamos con la lista de comunes (que saca justamente las de 8 a 10 caracteres más probadas), con Argon2id y con el límite de intentos, que vuelven impráctica la fuerza bruta en línea. Si se suma MFA, el mínimo de 8 pasa a cumplir.
- **Sin MFA.** Fuera del alcance del MVP. Queda como pendiente; con un segundo factor, el mínimo de 8 cumple NIST tal cual.
- **Límite por ventana, no "100 seguidos".** NIST pide que no haya más de 100 fallos seguidos por cuenta. Con una ventana deslizante el tope es por ventana (10 cada 15 minutos): nunca hay más de 10 intentos juntos, pero a lo largo de días un atacante paciente podría sumar más de 100 sin un login correcto en el medio. Un bloqueo definitivo a los 100 cumpliría la letra, pero dejaría que cualquiera bloquee una cuenta ajena, que es lo que NIST también pide evitar. Elegimos el bloqueo temporal; el tope por cuenta no se puede configurar por encima de 100. El equipo lo confirmó el 14/10: el límite queda **por ventana más un tope por IP**, y no se adopta la lectura literal de "100 seguidos".
- **Límite por IP con NAT.** En el campus muchas personas salen a internet por pocas IPs (NAT de la red de la universidad): un tope por IP bajo bloquearía a todo el campus por unos pocos errores de tipeo. Por eso el tope por IP es **conservador** (100 fallos en 15 minutos, holgado a propósito) y se calibra con datos del piloto. En producción la IP sale de `X-Forwarded-For` (`server.forward-headers-strategy: native`), porque Render atiende detrás de un proxy.
- **Lista de 2500, no millones.** NIST no fija un tamaño. Una lista local y chica no necesita servicios externos ni manda datos de nadie afuera, y cubre las contraseñas que más se prueban. Una lista de filtraciones completa (p. ej. Have I Been Pwned con k-anonymity) queda como mejora.
- **No es atómico.** El conteo y el registro de un fallo son dos consultas: dos intentos simultáneos pueden pasar el tope por uno o dos. Alcanza para frenar la fuerza bruta.

## Consecuencias

- Una contraseña con ñ, tildes o emojis tiene el mismo tope que una en ASCII. El front valida igual que el back (code points tras NFKC) y la lista común la valida solo el servidor: su error llega como violación del campo.
- Hashear con Argon2id cuesta más memoria (19 MiB por login en curso) y CPU que BCrypt; para el tráfico del MVP en Render alcanza. Los hashes ocupan unos 105 caracteres: V13 amplía `password_hash` a 255.
- Los hashes BCrypt viejos siguen validando hasta que cada persona entra de nuevo. Una cuenta que nunca vuelve queda con BCrypt: se puede forzar un reset más adelante si hace falta.
- Si alguien eligió su contraseña antes de #211 con caracteres que NFKC transforma (letras de ancho completo, ligaduras), su BCrypt viejo no coincide con la versión normalizada y tiene que usar "Olvidé mi contraseña". Con teclados comunes no pasa.
- Una contraseña vieja que ahora está en la lista común o tiene más de 64 caracteres sigue sirviendo para entrar: la política se aplica al elegirla, no al loguearse.
- La tabla `login_failure` y los tres parámetros nuevos de `app_config` vienen en V13. Cambiar los valores se hace con un `UPDATE` y reiniciando la app, como el resto de la configuración (ADR 0010). Por el [ADR 0017](0017-donde-viven-los-parametros.md) son parámetros de seguridad: en [#225](https://github.com/Benji-9/SmartElevate/issues/225) pasaron a `application.yml` (`app.login-throttle.*`, override por `APP_LOGIN_THROTTLE_*`) y `V14` borró las filas de `app_config`.
