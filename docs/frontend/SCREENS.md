# SmartElevate — Pantallas (especificación para Claude Code)

Describe las 9 pantallas del wireframe con medidas, textos y estados, para implementarlas **sin llamar a Figma**. Los tokens, componentes y clases (`--ink`, `radius-lg`, `text-title`, `Button`…) están en `DESIGN-SYSTEM.md`.

**Este archivo describe la versión móvil (390 px).** La disposición en modo Pantalla para laptop y el selector Pantalla / Teléfono están en `VIEW-MODES.md`.

Convenciones: medidas en px; "pad 16" = padding 16 en los cuatro lados, "pad 12/20" = vertical/horizontal; "gap" = separación entre hijos. Las cifras y textos de las tablas son ilustrativos; las pantallas leen el backend real (ver [Estado por pantalla](#estado-por-pantalla)). Ante contradicción manda `docs/openapi.json`.

## Mapa de rutas

| Ruta | Pantalla | Navegación inferior | Acceso |
|---|---|---|---|
| `/login` | 01 Login | no | público |
| `/registro` | 02 Registro | no | público |
| `/recuperar` | 02b Recuperar contraseña | no | público |
| `/recuperar/nueva?token=…` | 02c Contraseña nueva (link del mail) | no | público |
| `/verificar-email?token=…` | 02d Verificar email (link del mail) | no | público |
| `/privacidad` | Política de privacidad | no | público |
| `/terminos` | Términos de uso | no | público |
| `/` | 03 Inicio | sí (Inicio) | autenticado |
| `/reservar` | 04 Reservar turno | no (tiene header con volver) | autenticado |
| `/turno/:id` | 05 Turno confirmado | no | autenticado |
| `/check-in` | 06 Check-in (QR) | no | autenticado |
| `/check-in/ok` | 07 Viaje registrado | no | autenticado |
| `/perfil` | 08 Perfil y acceso prioritario | sí (Perfil) | autenticado |
| `/perfil/viajes` | Mis viajes (desde la lista de ajustes del perfil) | no (tiene header con volver) | autenticado |
| `/perfil/notificaciones` | Notificaciones (desde la lista de ajustes del perfil) | no (tiene header con volver) | autenticado |
| `/admin` | Layout del panel (`AdminLayout`); su índice es 09 Panel de administración | — (escritorio) | rol administrador |
| `/admin/prioritarios` | 09b Usuarios prioritarios, dentro del layout de `/admin` | — (escritorio) | rol administrador |
| `/admin/kioscos` | 09c Kioscos del QR, dentro del layout de `/admin` | — (escritorio) | rol administrador |
| `/admin/ascensores` | 09e Ascensores fuera de servicio, dentro del layout de `/admin` | — (escritorio) | rol administrador |
| `/admin/simulador` | 09d Simulador de kiosco, dentro del layout de `/admin` (solo si el servidor lo habilita) | — (escritorio) | rol administrador |
| `/qr/:ascensor` | 10 Kiosco QR del hall ([ADR 0016](../adr/0016-qr-firmado-con-hmac-y-kiosco-web.md), [ADR 0020](../adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)) | no | público, con la credencial del kiosco |
| `*` | 404 | no | público |

Flujo principal: `Login → Inicio → Reservar → Turno confirmado → Check-in → Viaje registrado → Inicio`. La tab "Reservar" lleva a `/reservar`, "Check-in" a `/check-in` y "Perfil" a `/perfil`.

## Reglas de negocio que la UI debe respetar

- Cada franja horaria tiene **capacidad de 10 personas**; al llegar a 10 se muestra "Completo" y no se puede elegir.
- La franja es una **salida de ascensor de ~2 min** ([ADR 0007](../adr/0007-modelo-de-turnos-y-reservas.md)); se reserva de 30 a 2 min antes.
- **Regla de pisos bajos** ([`asignacion.md`](../reglas/asignacion.md)): los trayectos cortos van por escalera, salvo **movilidad reducida**, que queda exenta (los docentes no). Hoy no se reserva con destino en los pisos **1 a 4** (umbral `low_floors_threshold` de la tabla de configuración, [#113](https://github.com/Benji-9/SmartElevate/issues/113)); el "0–4" del wireframe era de ejemplo. La regla la evalúa el servidor al reservar: hoy el contrato no expone la elegibilidad por piso, así que la UI no la marca de antemano y muestra el motivo de `LOW_FLOOR_DESTINATION` (ver §04), sin hardcodear pisos ni textos.
- El **acceso prioritario** se solicita desde el perfil subiendo un certificado de discapacidad (PDF) o un certificado médico (PDF o imagen); queda en estado "Pendiente de validación" hasta que se valida.
- El **check-in** se hace escaneando el QR que rota en la pantalla dentro de la cabina.
- Los núcleos son **Lima 1, Lima 2, Lima 3, Independencia 1 e Independencia 2** (en botones cortos: "Indep. 1", "Indep. 2"). El "Lima 3, ascensores 1–3, pisos 7–10" del wireframe es de ejemplo: la zonificación real ya está relevada ([asignación](../reglas/asignacion.md#ascensores-y-pisos-servidos)) y la manda el servidor.
- Autenticación con JWT; registro con email institucional, legajo y contraseña.

> **Ojo (repo):** el wireframe original usaba franjas de 5 minutos; no se implementan. Ante contradicción con la spec manda `docs/reglas/` (ADR 0007).

## Estado por pantalla

Con `VITE_USE_MOCKS=true` (solo `npm run dev`) el front usa datos simulados (`services/mocks.ts`); en tests, build y producción siempre pega al backend ([README](../../README.md#frontend)). Qué está conectado hoy:

| Pantalla | Estado | Endpoints / PR |
|---|---|---|
| 01 Login, 02 Registro, 02b/02c Recuperar contraseña, 02d Verificar email | Backend real | `/api/auth/*` ([#199](https://github.com/Benji-9/SmartElevate/pull/199), [#202](https://github.com/Benji-9/SmartElevate/pull/202), [#248](https://github.com/Benji-9/SmartElevate/pull/248)) |
| 03 Inicio, 04 Reservar, 05 Turno, Mis viajes | Backend real | `/api/reservations`, `/api/departures`, `/api/congestion` ([#241](https://github.com/Benji-9/SmartElevate/pull/241), [#243](https://github.com/Benji-9/SmartElevate/pull/243)) |
| 06 Check-in, 07 Viaje registrado | Backend real | `POST /api/check-ins` ([#239](https://github.com/Benji-9/SmartElevate/pull/239)). Sin ingreso manual ([ADR 0021](../adr/0021-check-in-sin-ingreso-manual.md)) |
| 08 Perfil: cabecera y Notificaciones | Backend real | `/api/users/me`, `/api/me/notification-preferences` ([#246](https://github.com/Benji-9/SmartElevate/pull/246)) |
| 08 Perfil: tarjeta "Acceso prioritario" (subir certificado) | **Pendiente**: sin endpoint (`/priority-requests/me` no está en el contrato); solo funciona con mocks | [#122](https://github.com/Benji-9/SmartElevate/issues/122), [#123](https://github.com/Benji-9/SmartElevate/issues/123), sujeto a [#24](https://github.com/Benji-9/SmartElevate/issues/24) |
| 09 Panel (KPIs, congestión) | Backend real | `/api/admin/kpis`, `/api/admin/shifts` ([#242](https://github.com/Benji-9/SmartElevate/pull/242)) |
| 09b Prioritarios | Backend real | `/api/admin/teachers/*`, `/api/admin/priority-users`, `/api/admin/users` ([#208](https://github.com/Benji-9/SmartElevate/pull/208), [#245](https://github.com/Benji-9/SmartElevate/pull/245)) |
| 09c Kioscos, 09d Simulador, 10 Kiosco QR | Backend real | `/api/admin/kiosks`, `/api/kiosks/me/qr-token` ([#234](https://github.com/Benji-9/SmartElevate/pull/234), [#236](https://github.com/Benji-9/SmartElevate/pull/236)) |
| 09e Ascensores fuera de servicio | Backend real | `/api/admin/elevators` ([#249](https://github.com/Benji-9/SmartElevate/pull/249)) |

---

## Estructura común (móvil)

> En una laptop esta estructura se ve dentro de un marco de teléfono (modo Teléfono) o se reacomoda a layout de computadora (modo Pantalla); ver `VIEW-MODES.md`.

- Frame de **390 × 844**. Se implementa como contenedor a pantalla completa con `max-width: 390px` centrado en escritorio.
- Esqueleto: `header` opcional → `main` (`padding` de 20 px lateral) → `BottomNav` opcional (fijo abajo).
- `PageHeader` interno: pad 8/20, gap 12, alineado al centro. `BackButton` = círculo de 36 px con fondo `--surface-subtle` y flecha "←" (18 px, 600); área táctil de 44 px. Título en `.text-heading`.

---

## 01 · Login — `/login`

`main`: pad 40/20/24/20, gap 16.

1. **Marca** (columna centrada, gap 12): logo `smartelevate-logo.svg` a 120 px; título `.text-title` centrado "Te damos la bienvenida a SmartElevate"; subtítulo 15 px `--ink-muted` centrado "Reservá tu turno de ascensor y llegá a tiempo a clase."
2. Espacio de 8 px.
3. `Input` "Email institucional" con el sufijo fijo `@uade.edu.ar` (ph `jmartinez`): se escribe solo el usuario. Ver [Email institucional](#email-institucional).
4. `Input` "Contraseña", placeholder `••••••••` (`type="password"`).
5. Link "¿Olvidaste tu contraseña?" — 13 px, 500, `--accent`, sin subrayado, alineado a la derecha; lleva a `/recuperar` (02b).
6. `Button` primary "Ingresar".
7. "¿No tenés cuenta? Registrate" — 14 px, `--ink-muted`, centrado; "Registrate" es un link a `/registro` en `--accent`.

Estados: error de credenciales → `error` en el `Input` de contraseña.

## 02 · Registro — `/registro`

`PageHeader` con volver + "Crear cuenta". `main`: pad 8/20/20/20, gap 14.

1. Intro 14 px `--ink-muted`: "Usá tu email institucional y tu legajo."
2. `Input` × 4: "Nombre y apellido" (ph `Juana Martínez`), "Email institucional" (sufijo fijo `@uade.edu.ar`, ph `jmartinez`), "Legajo" (ph `Ej: 1234567`, hint "Está en tu credencial UADE."), "Contraseña" (ph `••••••••`). La contraseña lleva el `hint` "Entre 8 y 64 caracteres. No exigimos símbolos ni mayúsculas." y el cliente valida el largo igual que el servidor (code points tras NFKC). La lista de contraseñas comunes la valida solo el servidor y su error va al lado del campo ([ADR 0015](../adr/0015-politica-de-contrasenas-nist-800-63b.md), [usuarios y prioridad](../reglas/usuarios-y-prioridad.md#contraseña)).
3. Etiqueta "Tipo de usuario" (13 px, 500, `--ink-muted`) y **selector segmentado** (2 opciones de igual ancho, gap 8, pad 11/8, `--radius-sm`, texto 14 px / 600): **Estudiante** (seleccionada por defecto: fondo `--accent`, texto `--on-accent`) y **Docente** (no seleccionada: fondo `--surface`, borde 1 px `--border`, texto `--ink`). Cada opción lleva un punto de 10 px a la izquierda (gap 8): Estudiante → `--User-Alumnos`, Docente → `--User-Docentes`. El wireframe tenía una tercera opción, **Personal**, que se sacó en [#32](https://github.com/Benji-9/SmartElevate/issues/32). El tipo es **declarativo**: elegir Docente no da prioridad, queda pendiente hasta que un ADMIN lo aprueba.
4. **Aviso informativo** (pad 14, `--radius-md`, fondo `--accent-tint`, gap 4): título "¿Tenés movilidad reducida?" 14 px / 600 `--accent`; texto 13 px "Después de registrarte podés solicitar acceso prioritario desde tu perfil."
5. Texto 14 px `--ink-muted`: "Al crear tu cuenta aceptás los Términos de uso y la Política de privacidad." (links a `/terminos` y `/privacidad`).
6. `Button` primary "Crear cuenta".
7. Footer legal (ver abajo).

## 02b · Recuperar contraseña — `/recuperar`

No hay frame en Figma: usa la misma estructura y componentes que 02 Registro. Contrato en [#138](https://github.com/Benji-9/SmartElevate/issues/138).

`PageHeader` con volver (a `/login`) + "Recuperar contraseña". `main` como 02.

1. Intro 14 px `--ink-muted`: "Te mandamos un link a tu email institucional para que elijas una contraseña nueva."
2. `Input` "Email institucional" con el sufijo fijo `@uade.edu.ar`, igual que el login.
3. `Button` primary "Enviar link" → `POST /api/auth/password/forgot`.

Estados: el servidor responde **202 siempre**, exista o no la cuenta, así que la confirmación no lo revela: título "Revisá tu email" y "Si {email} tiene una cuenta, te mandamos un link para elegir una contraseña nueva. Vence en unos minutos y sirve una sola vez.", con link "Volver a ingresar". Error del servidor (p. ej. límite de pedidos) → aviso `--danger-tint` sobre el botón.

## 02c · Contraseña nueva — `/recuperar/nueva?token=…`

Es la pantalla a la que lleva el link del mail. Misma estructura que 02b, con título "Contraseña nueva".

1. `Input` "Contraseña nueva" y `Input` "Repetí la contraseña" (ph `••••••••`, `autocomplete="new-password"`). La UI solo valida que no esté vacía y que coincidan; la política la valida el servidor y su error va al lado del campo.
2. `Button` primary "Guardar contraseña" → `POST /api/auth/password/reset` con `{ token, newPassword }`.

Estados: OK → "Listo, cambiaste tu contraseña" + link "Ir a ingresar". Sin `token` en la URL, o token vencido / ya usado (400) → aviso `--danger-tint` con el motivo y link "Pedir otro link" a `/recuperar`.

## 02d · Verificar email — `/verificar-email?token=…`

No hay frame en Figma. Es la pantalla a la que lleva el link del mail de registro ([#193](https://github.com/Benji-9/SmartElevate/issues/193)). Misma estructura que 02c; `PageHeader` con volver (a `/login`) + "Verificar email".

Al abrirla llama sola a `POST /api/auth/verify` con `{ token }` (es POST a propósito: los antivirus de los mails que abren links no gastan el token).

Estados:
- **Verificando** (`role="status"`): "Verificando tu email…".
- **OK** (204): título "Listo, verificamos tu email", "Ya podés ingresar a SmartElevate." y link "Ir a ingresar".
- **Error** (sin `token` en la URL, o 400 por token inexistente / usado / vencido): aviso `--danger-tint` con el mensaje del servidor (distinto por caso; sin token: "Este link no es válido. Pedí uno nuevo."), "Pedí un link nuevo a tu email institucional." y el bloque **Reenviar**.

### Reenviar el email de verificación (`ResendVerification`)

Bloque reutilizable en 02d, en 02 (pantalla "Revisá tu email" tras registrarse) y en 01 (login con 403 por email sin verificar). `POST /api/auth/resend-verification` con `{ email }`.

- `Button` secondary "Reenviar email de verificación". Si el email no se conoce (02d) se pide antes con el campo "Email institucional" (ver abajo); en 01 y 02 se usa el ya tipeado.
- **202** (exista o no la cuenta): aviso `--accent-tint` (`role="status"`) "Si {email} tiene una cuenta sin verificar, te mandamos un link nuevo." No confirma que la cuenta exista.
- **429**: aviso `--danger-tint` "Ya te mandamos un email hace poco." y el botón queda deshabilitado con cuenta regresiva "Podés pedir otro en {n} s" según el header `Retry-After` (60 s si falta). Al llegar a 0 vuelve a "Reenviar email de verificación".
- Otro error: aviso `--danger-tint` con `ApiError.message`.

## Email institucional

Login, Registro y 02b usan el mismo campo ([#162](https://github.com/Benji-9/SmartElevate/issues/162)): `Input` con `suffix="@uade.edu.ar"` (constante `UADE_DOMAIN` en `features/auth/validation.ts`).

- Se escribe solo el usuario; el sufijo `@uade.edu.ar` queda fijo a la derecha dentro del control (`.text-body`, `--ink-muted`) y el email completo se arma al enviar.
- El sufijo entra en `aria-describedby`: el lector anuncia "Email institucional, @uade.edu.ar".
- `type="text"`, `autocomplete="username"`, `autocapitalize="none"`, `spellcheck="false"`.
- Si se pega o autocompleta el email completo con `@uade.edu.ar`, el campo se queda con el usuario. Validación al enviar: vacío → "Ingresá tu email institucional."; con `@` (otro dominio) → "Usá tu email @uade.edu.ar."; con espacios → "Escribilo sin espacios.". Los espacios de los bordes se recortan.
- Es ayuda de UI: el servidor sigue validando el dominio.

## Campos de contraseña

Todo `Input` con `type="password"` (Login, Registro y 02c) trae a la derecha, dentro del control, un `button type="button"` "Mostrar" / "Ocultar" (13 px, 500, `--accent`, área táctil de 44 × 44 px) con nombre accesible "Mostrar contraseña" / "Ocultar contraseña". Al enviar el formulario el campo vuelve a `type="password"` ([#156](https://github.com/Benji-9/SmartElevate/issues/156)).

## Footer legal y páginas legales

No hay frame en Figma ([#145](https://github.com/Benji-9/SmartElevate/issues/145)).

- **Footer legal** (`LegalFooter`): al final de Login, Registro, 02b, 02c, Perfil y las dos páginas legales. Columna centrada, gap 4, 12 px `--ink-muted`: fila de links (500, `--accent`, sin subrayado, área táctil de 44 px) **Privacidad · Términos de uso · Contacto** (`mailto:` al email del proyecto) y debajo "SmartElevate · Proyecto académico de estudiantes de UADE".
- **Páginas legales** (`/privacidad`, `/terminos`): `PageHeader` con volver + título; "Última actualización" 13 px `--ink-muted`; secciones numeradas con `h2` de 16 px / 600 y texto 14 px con interlineado 1,55. La leyenda obligatoria de la AAIP (Disposición 10/2008) va en un aviso `--accent-tint` dentro de "Tus derechos". En Pantalla usan la columna de 480 px.
- Contenido: la política sigue la Ley 25.326, el Decreto 1558/2001, las normas de la AAIP y el Convenio 108/108+, con los derechos del RGPD; los términos son los de un proyecto académico. **No es asesoramiento legal**: validar con la cátedra ([#24](https://github.com/Benji-9/SmartElevate/issues/24)). Si cambian proveedores, regiones o el tratamiento de datos, actualizar `PrivacyPage.tsx`.

## 03 · Inicio — `/`

`main`: pad 12/20/16/20, gap 16. `BottomNav` con **Inicio** activa.

1. **Saludo** (fila, gap 12): columna con "Hola, Juana" (`.text-title`) y "Lunes 28/9" (14 px `--ink-muted`; la sede queda **pospuesta**: no hay de dónde sacarla, [#86](https://github.com/Benji-9/SmartElevate/issues/86)); a la derecha, avatar circular de 44 px (`--surface-subtle`).
2. **Tarjeta "Próxima clase"** — **pospuesta**: todavía no hay fuente del horario de cursada ([#86](https://github.com/Benji-9/SmartElevate/issues/86)); no implementar por ahora. (pad 18, gap 6, `--radius-xl`, fondo `--accent`): rótulo `.text-overline` "PRÓXIMA CLASE" en `--on-dark-muted`; nombre de la materia 17 px / 700 `--on-accent`: "Seminario de Gestión de Tecnología"; detalle 14 px "Aula 705 · Piso 7 · Lima 3 · 7:45 hs" en `--on-dark-muted`; separación 6; botón interno blanco (pad 10/14, `--radius-sm`, texto 14 px / 600 `--accent`) "Reservar turno para esta clase" → `/reservar`.
3. **Tarjeta "Tu turno"** (pad 16, gap 10, `--radius-xl`, borde 1 px `--line`): fila con "Tu turno" (`.text-card-title`) y `Chip` accent "Confirmado"; cuenta regresiva 18 px / 700 `--accent` "Sale en 6 min" (con el `departsAt` de la API, se actualiza cada ~30 s; "Sale ahora" al llegar la hora. El destacado por límite de cancelación espera que la API exponga ese instante, [#155](https://github.com/Benji-9/SmartElevate/issues/155)); línea 15 px / 500 "7:25 hs · Lima 3 · Ascensor 33 · Piso 7" (`departureAt`, `coreName`, `elevator`: la reserva no trae la duración de la franja, [#214](https://github.com/Benji-9/SmartElevate/issues/214)); ayuda 13 px `--ink-muted` "Escaneá el QR de la pantalla del ascensor al subir."; `Button` primary "Hacer check-in" → `/check-in`. Si no hay turno, ocultar la tarjeta.
4. **"Congestión ahora"** (gap 10): fila de encabezado con "Congestión ahora" (`.text-card-title`) y "Actualizado hace 1 min" (12 px `--ink-muted`); debajo un `CongestionRow` por núcleo (`GET /api/congestion`: `value` = `occupancyPercent / 100`, detalle "62,5 % de los lugares reservados"; se vuelve a pedir cada `refreshAfterSeconds`):

| Núcleo | level | value |
|---|---|---|
| Lima 1 | baja | 0.35 |
| Lima 2 | media | 0.6 |
| Lima 3 | alta | 0.9 |
| Independencia 1 | alta | 0.95 |
| Independencia 2 | media | 0.6 |

## 04 · Reservar turno — `/reservar`

`PageHeader` con volver + "Reservar turno". `main`: pad 8/20/20/20, gap 12.

**Datos (contrato, #217):** el backend modela por **ascensor**. El front arma los núcleos con `GET /api/buildings` (nombre, rango de pisos y núcleos) y `GET /api/elevators` (pisos servidos por cada ascensor); las franjas salen de `GET /api/departures?building=…`, que ya viene filtrado para el usuario (solo los ascensores que puede usar, p. ej. los dedicados de IND1 solo para su categoría) con `remainingSeats` = lugares que **él** puede tomar. El front no decide elegibilidad: lo que no valga lo rechaza `POST /api/reservations` con su `code`. Si `GET /api/reservations/active` trae una reserva (204 = no hay), no se ofrece el formulario: "Ya tenés un turno activo para la salida de las 14:32. Para reservar otro, primero cancelalo." y el link "Ver mi turno".

**Precarga del último viaje:** si la persona ya reservó desde ese dispositivo, el formulario abre con el núcleo, el origen y el destino de ese turno (guardados en `localStorage` al reservar con éxito), y arriba un aviso (pad 12, `--accent-tint`) "Usamos tu último viaje: Lima 3 · PB → 7. Podés cambiarlo abajo.". Lo que ya no sea válido (núcleo que no existe o sin ascensores activos, piso que ya no sirve ningún ascensor) se ignora sin error. **La franja nunca se precarga:** depende del cupo del momento. Al cambiar cualquier elección el aviso se va.

1. Etiqueta "Edificio" (14 px / 600) y **selector de núcleo** (pills en fila con wrap, gap 8, pad 8/14, `--radius-pill`, texto 14 px / 500): los núcleos de `/buildings` que tienen algún ascensor activo, en el orden de los edificios (Lima 1 · Lima 2 · **Lima 3 (seleccionado)** · Indep. 1 · Indep. 2). Seleccionado: fondo `--accent`, texto `--on-accent`; resto: `--surface`, borde 1 px `--border`.
2. **Piso de origen** (desplegable nativo): los pisos donde para algún ascensor del núcleo, dentro del rango del edificio.
3. Etiqueta "Piso de destino" y **selector de piso**: botones de **50 × 44** (`--radius-sm`, número 15 px / 600) con wrap y gap 8, con los pisos de los ascensores que paran en el origen (cada batería tiene los suyos, p. ej. L2), sin el origen. Seleccionado: `--accent`; resto: `--surface` con borde `--border`. **La regla de pisos bajos no se marca de antemano**: el contrato no expone qué pisos puede elegir cada usuario (el umbral está en `app_config` y no se hardcodea); si el destino no vale, el servidor responde `LOW_FLOOR_DESTINATION` con el motivo (p. ej. "Para ir a los pisos 1 a 4 usá la escalera.") y se pide otro destino.
4. Etiqueta "Franja horaria" y **lista de franjas** (gap 8): las salidas de los ascensores del núcleo que paran en los pisos elegidos, **agrupadas por hora** (todos los ascensores salen a la misma hora). En cada hora se ofrece la salida con más lugares libres para el usuario. Cada franja: fila pad 12/14, gap 12, `--radius-md`, borde 1 px `--line`; a la izquierda la hora de salida (15 px / 600) y el ascensor ("· Ascensor 12", 13 px / 500 `--ink-muted`), y la barra de ocupación (120 × 6, pista `--line`, relleno `--accent`, ancho = `(capacity − remainingSeats) / capacity`); luego "6 libres" (13 px / 500 `--ink-muted`); a la derecha radio de 20 px. La franja muestra solo la hora: el contrato no trae la duración de la salida.

| Franja | Libres | Estado |
|---|---|---|
| 14:30 · Ascensor 10 | 0 | **Completo**: deshabilitada, hora e indicador atenuados, texto "Completo". También cuando lo que queda está reservado para prioritarios (`remainingSeats` ya lo descuenta) |
| 14:32 · Ascensor 12 | 2 | disponible |
| 14:34 · Ascensor 11 | 5 | **seleccionada**: borde 2 px `--accent`, fondo `--accent-tint`, radio relleno |
| 14:36 · Ascensor 10 | 8 | disponible |

Sin salidas: "No hay salidas que puedas reservar en este núcleo ahora. Probá en unos minutos." (p. ej. IND1 para quien no tiene la prioridad de sus ascensores dedicados).

5. **Pie fijo** mientras el contenido scrollea, con:
   - El **resumen** de lo elegido en una línea (15 px / 600): "Lima 3 · Piso 7 · 14:34". Sin nada elegido no se muestra.
   - Mientras falte algo, el **próximo paso** (13 px, `--ink-muted`): "Falta elegir el edificio", "…el piso de origen", "…el piso de destino" o "…la franja horaria". Va asociado al botón con `aria-describedby`.
   - `Button` primary con la franja elegida: "Confirmar turno · 14:34" (deshabilitado hasta completar todo).
   - Si el servidor rechaza la reserva, el **motivo** (`role="alert"`, fondo `--danger-tint`) es el `message` del `ApiError` (ya viene en español y con los valores de la regla); el `code` decide qué más pasa:

| `code` | Qué hace la pantalla |
|---|---|
| `TURN_FULL` (incluye "los lugares que quedan están reservados para prioritarios"), `ELEVATOR_NOT_BOOKABLE`, `OUTSIDE_BOOKING_WINDOW`, `DEPARTURE_NOT_FOUND` | Recarga las salidas, deselecciona la franja y lleva el foco a "Franja horaria" para elegir otra |
| `SAME_FLOOR`, `FLOOR_OUT_OF_RANGE`, `FLOOR_NOT_SERVED`, `LOW_FLOOR_DESTINATION` | Deselecciona el destino |
| `ACTIVE_LIMIT_REACHED` | Vuelve a pedir `/reservations/active` y muestra el aviso de turno activo con "Ver mi turno" |
| `NO_SHOW_SUSPENDED` | Solo el motivo, que dice hasta cuándo ("…no podés reservar hasta el 14/10 a las 12:58.") |
| `DAILY_LIMIT_REACHED` y sin `code` | Solo el motivo (sin conexión: "No pudimos conectarnos. Probá de nuevo.") |

## 05 · Turno confirmado — `/turno/:id`

Sin header ni tabs. `main`: pad 32/20/24/20, gap 16.

1. **Hero** centrado (gap 10): círculo de éxito de 88 px (`--accent-tint-strong`) con tilde de trazo 5 px `--accent`, puntas redondeadas; título `.text-title` "¡Turno confirmado!"; 15 px `--ink-muted` "Te esperamos en el hall de Lima 3."
2. **Detalle del turno** (pad 16, gap 12, `--radius-lg`, borde `--line`): filas clave (14 px `--ink-muted`) / valor (14 px / 600), con justificación entre extremos:

| Clave | Valor |
|---|---|
| Edificio | Lima 3 |
| Ascensores | 1 a 3 (pisos 7–10) |
| Destino | Piso 7 · Aula 705 |
| Franja | 7:25 – 7:30 |
| Ocupación | 5 / 10 personas |

> **Repo (#214):** con `ReservationResponse` el detalle muestra Edificio (`buildingName`), Ascensor (`elevator` · `coreName`), Recorrido y **Salida** "7:25 hs" (`departureAt`). La ocupación y los pisos del ascensor no vienen en la reserva y no se muestran. El hall del hero sale de `coreName`.

3. **Ayuda de check-in** (pad 14, `--radius-md`, `--accent-tint`, 13 px): "Al subir, escaneá el QR que aparece en la pantalla dentro del ascensor para registrar tu viaje."
4. Espacio flexible que empuja los botones al pie.
5. `Button` primary "Ir a check-in" → `/check-in`; `Button` secondary "Cancelar turno", que pide confirmación en un diálogo modal: título "¿Cancelar el turno?" (si el servidor avisa que cuenta como falta, el título es "Si cancelás ahora, cuenta como falta"); botones "Mantener turno" (primary, primero y con el foco al abrir) y "Cancelar turno" (secondary). La acción destructiva nunca lleva el énfasis.

## 06 · Check-in (QR) — `/check-in`

Pantalla **oscura**: fondo `--surface-dark`; barra de estado, título y textos en `--on-dark`. Header con volver (círculo con fondo blanco al 15 %) + "Check-in" en `.text-heading` blanco.

1. **Vista de cámara** (columna centrada, pad 40/20/24/20, gap 20): visor de **250 × 250**, `--radius-2xl`, borde 3 px `--on-dark`, con la imagen de la cámara adentro (en el mockup, un cuadrado de 160 px translúcido); texto 16 px / 600 `--on-dark` centrado "Apuntá la cámara al QR de la pantalla del ascensor"; texto 13 px `--on-dark-muted` "El código rota: escanealo desde dentro de la cabina."
2. **Hoja inferior** (`--surface`, esquinas superiores `--radius-2xl`, pad 20/20/32/20, gap 12): fila con "Tu turno" (`.text-card-title`) y `Chip` accent "7:25 hs" (`departureAt` de `GET /api/reservations/active`); 14 px `--ink-muted` "Lima 3 · Ascensor 33 · Piso 7".

Al leer un QR, manda el texto tal cual a `POST /api/check-ins` (`{ token }`) y con la respuesta va a `/check-in/ok`. **No hay ingreso manual del código** ([ADR 0021](../adr/0021-check-in-sin-ingreso-manual.md)): el token tiene ~80 caracteres opacos.

**Rechazos** (aviso `role="alert"` sobre la cámara con el `message` del servidor; se decide por `code`):

| `code` | Qué agrega la app | ¿Sigue escaneando? |
| --- | --- | --- |
| `INVALID_QR_TOKEN` (400) | nada: el mensaje ya pide escanear el QR en pantalla | sí, con el QR siguiente (el mismo token no se reenvía) |
| `OUTSIDE_CHECK_IN_WINDOW` (409) | "Escaneá el QR cerca de la hora de salida de tu turno." (sin números: la ventana sale de `app_config`) | sí, con el QR siguiente |
| `NO_ACTIVE_RESERVATION` (409) | link "Reservá un turno" → `/reservar` | no |
| `ALREADY_CHECKED_IN` (409) | link "Volver al inicio" → `/` | no |

Sin red u otro error: "No pudimos conectarnos. Probá de nuevo."

**Permiso de cámara:** se pide recién cuando se confirma que hay un turno activo. Sin turno no se pide: la vista de cámara queda vacía y la hoja muestra "No tenés un turno activo. Reservá un turno". Si falla la carga del turno, la cámara se pide igual (el servidor valida el check-in) y la hoja muestra el error. Permiso negado: "No tenemos permiso para usar la cámara" · "Para hacer check-in hay que escanear el QR del ascensor. Habilitá la cámara desde la configuración del navegador." Sin cámara: "No pudimos usar la cámara" · "Para hacer check-in hay que escanear el QR del ascensor. Probá con otro navegador o con el celular."

## 07 · Viaje registrado — `/check-in/ok`

Sin header ni tabs. `main`: pad 48/20/24/20, gap 16.

Muestra la respuesta de `POST /api/check-ins` (llega en el `state` de la navegación; sin ella, redirige a `/`).

1. **Hero** igual al de la 05: título "Viaje registrado"; detalle 15 px `--ink-muted` "Ascensor 33 · 7:27 hs" (`elevator` escaneado y `checkedInAt`; la respuesta no trae el núcleo). Cambia según el resultado ([check-in](../reglas/check-in-qr.md#resultados)): con *otro ascensor*, título "Viaje registrado en otro ascensor"; con *fuera de hora* (cuenta como falta), sin tilde: signo de advertencia sobre `--Status-Alta-tint` en `--Status-Alta` y título "Llegaste fuera de tu franja: cuenta como falta".
2. **Tarjeta "a tiempo"** (pad 16, gap 4, `--radius-lg`, `--accent-tint`): "Check-in a tiempo" 15 px / 600 `--accent`; debajo, 13 px, el detalle del resultado y "Salida de tu turno: **7:26 hs**" (`departureAt`; la respuesta no trae la duración de la franja). Según `outcome`:
   - `ON_TIME`: "Check-in a tiempo" · "Subiste dentro de tu franja. ¡Gracias por usar tu turno!"
   - `OTHER_ELEVATOR` (fondo neutro): "Check-in en otro ascensor" · "Reservaste el ascensor 33 y escaneaste el 34. Quedó registrado y no cuenta como falta." (`reservedElevator` y `elevator`).
   - `LATE` (`--danger-tint`; la reserva queda `NO_SHOW`): "Check-in fuera de hora" · "El check-in fue después de la hora de tu salida, así que cuenta como falta. Igual queda registrado que viniste." La hora y el aula de la clase ("Tu clase empieza a las 7:45 en el Aula 705.") quedan **pospuestas** hasta tener el horario de cursada ([#86](https://github.com/Benji-9/SmartElevate/issues/86)).
3. **Encuesta de espera** — **no se muestra**: queda **fuera del MVP** ([alcance](../alcance-mvp.md)): el backend no tiene dónde guardarla ni medir la espera real, y no se inventa contrato. Si se retoma, el componente está en el historial del repo (`features/check-in/WaitFeedback.tsx`, [#238](https://github.com/Benji-9/SmartElevate/issues/238)). Spec para entonces: (pad 16, gap 12, `--radius-lg`, borde `--line`): pregunta 15 px / 600 "¿Cuánto esperaste el ascensor?"; 4 opciones de igual ancho (pad 10/4, `--radius-sm`, 13 px / 500): "< 2 min", "2–5", "5–10", "> 10" (opción elegida: borde `--accent`, fondo `--accent-tint`, texto `--accent`); nota 12 px `--ink-muted` "Nos ayuda a medir la congestión real."
4. Espacio flexible; `Button` primary "Volver al inicio" → `/`.


## 08 · Perfil y acceso prioritario — `/perfil`

`PageHeader` sin volver, título "Mi perfil". `main`: pad 8/20/20/20, gap 16. `BottomNav` con **Perfil** activa.

1. **Cabecera** (fila, gap 14): avatar de 60 px; columna (gap 4): "Juana Martínez" (18 px / 700), `Chip` tone `alumnos` "Estudiante", "Legajo 1234567" (13 px `--ink-muted`), "jmartinez@uade.edu.ar" (13 px `--ink-muted`). El chip cambia de tono con el tipo de usuario.
2. **Tarjeta "Acceso prioritario"** (pad 16, gap 12, `--radius-lg`, borde `--line`):
   - fila: "Acceso prioritario" (`.text-card-title`) + `Chip` accent "Pendiente de validación";
   - texto 13 px `--ink-muted`: "Si tenés movilidad reducida, subí tu certificado de discapacidad o un certificado médico para habilitar turnos prioritarios (incluye los pisos bajos)."
   - **Zona de carga** (pad 20, gap 6, `--radius-sm`, borde discontinuo 1,5 px `--accent`, fondo `--accent-tint`, contenido centrado): ícono de 28 px, "Subir certificado" 14 px / 600 `--accent`, "PDF o imagen" 12 px `--ink-muted`.
   - **Archivo cargado** (fila pad 10/12, gap 10, `--radius-sm`, `--surface-subtle`): ícono de archivo, nombre 13 px / 500 (`certificado_discapacidad.pdf`), acción "Quitar" 12 px / 500 `--accent`.
   - Estados del chip, según el `status` de la solicitud: `PENDING` → "Pendiente de validación", `APPROVED` → "Aprobado" (o "Aprobado (vence el {día/mes})" si tiene vencimiento), `REJECTED` → "Rechazado".
3. **Lista de ajustes** (`--radius-lg`, borde `--line`): filas pad 14/16, 15 px / 500, flecha "→" a la derecha en `--ink-muted`, divisor `--line` entre filas: "Mis viajes" y "Notificaciones". Debajo, separado de la lista y sin flecha (es una acción, no un destino), `Button` secondary "Cerrar sesión".

## 09 · Panel de administración — `/admin`

Escritorio **1440 × 900**. Layout en dos columnas.

**Barra lateral** (248 px, fondo `--surface-dark`, pad 28/16, gap 6): "SmartElevate" (20 px / 700 `--on-dark`), "Panel UADE" (12 px `--on-dark-muted`), separación 20 y navegación (cada ítem pad 10/12, gap 10, `--radius-sm`, ícono de 18 px + texto 14 px): **Dashboard** (activo: fondo blanco al 12 %, texto 600 `--on-dark`). **Usuarios prioritarios** (activo en `/admin/prioritarios`, ver [09b](#09b--usuarios-prioritarios--adminprioritarios)). **Kioscos** ([09c](#09c--kioscos-del-qr--adminkioscos)), **Ascensores** ([09e](#09e--ascensores-fuera-de-servicio--adminascensores)) y **Simulador de kiosco** ([09d](#09d--simulador-de-kiosco--adminsimulador), solo con el simulador habilitado). Turnos, Núcleos y ascensores y Reportes (`--on-dark-muted`) se agregan **cuando existan**: no se muestran ítems "Próximamente", que compiten con lo real y dan pistas falsas de navegación ([#158](https://github.com/Benji-9/SmartElevate/issues/158)).

**Contenido** (pad 32, gap 24):

1. **Encabezado**: a la izquierda "Congestión y uso de ascensores" (`.text-title-lg`) y "Lunes 28/9 · Sede Lima · Turno mañana (7:00–12:15)" (14 px `--ink-muted`); a la derecha los filtros de período, sede y **turno de cursada** ("Turno mañana"). Los turnos y sus horarios los manda el servidor (tabla de configuración, ver [`kpis.md`](../reglas/kpis.md#filtros)).
2. **4 tarjetas KPI** en fila (gap 16, cada una pad 20, gap 6, `--radius-lg`, borde `--line`): etiqueta 13 px `--ink-muted`, cifra `.text-kpi`, detalle 12 px / 500 `--accent`.

| Etiqueta | Cifra | Detalle |
|---|---|---|
| Espera promedio | 4,1 min | % que espera 5–10 min y la línea base de [`kpis.md`](../reglas/kpis.md); debajo, un `Chip` con la variación en puntos: "▼ 25,5 pts · mejor que la línea base" (tono `baja`), "▲ … peor que la línea base" (tono `alta`) o "Igual que la línea base" (`neutral`). Flecha, color **y** texto ([#158](https://github.com/Benji-9/SmartElevate/issues/158)); el "−38 %" del wireframe era de ejemplo |
| Turnos reservados | 1.284 | hoy |
| Check-ins realizados | 87% | de los turnos reservados |
| Ocupación promedio | 7,6 / 10 | personas por franja |

Turnos, check-ins y ocupación llevan debajo un `Chip` `neutral` contra el período anterior (`previous`, [#180](https://github.com/Benji-9/SmartElevate/issues/180)): "▲ Subió 8 % vs. ayer" / "los 7 días anteriores" / "los 30 días anteriores" (check-ins, en puntos), o "Igual que ayer"; sin `previous` no se muestra. Un KPI en `null` muestra **"Sin datos"** como cifra y no se compara; hoy la espera siempre (necesita la encuesta, fuera del MVP), y check-ins u ocupación mientras no haya salidas resueltas. En la tabla por núcleo, igual: "Sin datos" en la celda y en el `Chip` de estado.

3. **Fila de dos tarjetas** (gap 16, mismo ancho):
   - **Gráfico "Reservas por franja (turno mañana)"** (pad 20, gap 16, `--radius-lg`, borde `--line`): título `.text-card-title`; barras verticales de 28 px de ancho, `--radius` 6, área de 240 px de alto, alineadas al fondo, con la hora debajo (11 px `--ink-muted`). Una barra por hora de `reservationsByHour` (alto proporcional al máximo). Barras de las horas más cargadas (≥ 80 % del máximo) en `--accent` sólido, el resto en `--accent` al 45 %.
   - **Tabla "Estado por núcleo"** (pad 20, `--radius-lg`, borde `--line`): columnas **Núcleo · Reservas · Ocupación · Espera · Estado**; encabezado 13 px / 600 `--ink-muted`; filas 13 px con pad 12/0 y divisor `--line`. La columna Estado es un `Chip` con tono `baja` / `media` / `alta`.

| Núcleo | Reservas | Ocupación | Espera | Estado |
|---|---|---|---|---|
| Lima 1 | 212 | 58% | 2,1 min | Baja |
| Lima 2 | 298 | 71% | 3,4 min | Media |
| Lima 3 | 356 | 86% | 5,2 min | Alta |
| Indep. 1 | 241 | 90% | 6,0 min | Alta |
| Indep. 2 | 177 | 66% | 3,0 min | Media |


### 09b · Usuarios prioritarios — `/admin/prioritarios`

Sin frame de Figma: se arma con los componentes del panel (tarjeta `.admin-card`, tabla de "Estado por núcleo", `Chip`, `Button`). Solo rol `ADMIN` (el servidor valida con 403). Reglas en [`usuarios-y-prioridad.md`](../reglas/usuarios-y-prioridad.md#tipo-de-usuario-declarado).

**Contenido** (mismo pad 32 y gap 24 del Dashboard). `h1` `.text-title-lg` "Usuarios prioritarios" y debajo (14 px `--ink-muted`) "Validá a los docentes y gestioná quién tiene prioridad." Dos tarjetas apiladas, cada una con `h2` `.text-card-title` y tabla (misma tipografía y divisores `--line` que "Estado por núcleo"):

1. **"Docentes a validar"**: columnas **Nombre · Email · Legajo · Registro · Acciones**. Registro = fecha en Buenos Aires ("29/9/2026"). Acciones: `Button` primary "Aprobar" y secondary "Rechazar" (ancho del contenido, 48 px de alto), con nombre accesible "Aprobar a {nombre}" / "Rechazar a {nombre}".
   - **Rechazar pide confirmación** en la misma fila: el texto "¿Rechazar a {nombre}?" reemplaza los botones, con "Sí, rechazar" (primary) y "Cancelar" (secondary, con el foco). Aprobar es directo y reversible desde la otra lista (revocar).
   - Al confirmar, la fila sale de la cola sin recargar la página. Aprobar la pasa a la lista de prioritarios con categoría Docente. Rechazar la deja como usuario común.
   - Vacía: "No hay docentes para validar." (`.page-placeholder`).
2. **"Usuarios con prioridad"**: columnas **Nombre · Email · Legajo · Categoría · Acciones**. Categoría = `Chip` (`docentes` "Docente" o `accent` "Movilidad reducida"), siempre con texto. Acción: secondary "Revocar" ("Revocar a {nombre}"), también con confirmación en la fila ("¿Revocar la prioridad de {nombre}?", "Sí, revocar" / "Cancelar").
   - Vacía: "Todavía no hay usuarios con prioridad."

**Estados**: cada tarjeta carga y falla por separado. Cargando: "Cargando…" (`role="status"`); error: mensaje del servidor con `role="alert"` y "Reintentar". Si falla una acción, el error aparece en un `role="alert"` dentro de la tarjeta y la fila queda como estaba. Si el servidor responde **404** (usuario inexistente o sin prioridad vigente) o **409** (ya aprobado, no se declaró docente), la fila quedó vieja —otro admin ya la resolvió—: se muestra el mensaje del servidor seguido de "Actualizamos la lista." y la tarjeta vuelve a pedir su lista.

**Endpoints** (tag `admin-priority` de `docs/openapi.json`): `GET /api/admin/teachers/pending`, `POST /api/admin/teachers/{id}/approve` y `/reject`, `GET /api/admin/priority-users`, `DELETE /api/admin/priority-users/{id}`.

**Cambiar roles**: tercera tarjeta, **"Roles de usuarios"** (mismo patrón que las anteriores; el rol solo lo cambia un `ADMIN`).

- **Buscador**: `Input` "Buscar usuario" con ayuda "Por nombre, email o legajo (mínimo 3 caracteres)". Busca solo después de una pausa de ~300 ms sin tipear (debounce) y con 3 o más caracteres (el servidor exige el mismo mínimo: 400 si no). Con menos, no pide nada y muestra la ayuda.
- **Resultados**: tabla **Nombre · Email · Legajo · Rol · Acciones**. Rol = `Chip` con texto ("Usuario", "Revisor", "Administrador"). Acciones: `select` nativo "Rol de {nombre}" con las tres opciones (el valor actual preseleccionado) y, al elegir otro, la confirmación en la misma fila: "¿Cambiar a {nombre} de {rol actual} a {rol nuevo}?" con "Sí, cambiar" (primary) y "Cancelar" (secondary, con el foco, que además restaura el valor del select). Nunca se cambia el rol sin confirmar.
- **Uno mismo**: la fila de la persona logueada no ofrece el selector (texto "Es tu cuenta"); el servidor igual responde 409 si se intenta.
- Paginación por cursor: si el servidor devuelve `nextCursor`, botón secondary "Ver más resultados" que agrega la página siguiente.
- Al confirmar, la fila se actualiza con el rol nuevo sin recargar; si falla, `role="alert"` con el mensaje del servidor y el rol queda como estaba. Con 404 (usuario borrado) se agrega "Actualizamos la lista." y se vuelve a buscar.
- **Estados**: sin búsqueda, la tarjeta solo muestra la ayuda; cargando "Buscando…" (`role="status"`); sin resultados "No encontramos usuarios para «{texto}»."; error del servidor en `role="alert"` con "Reintentar".

**Endpoints** (tag `admin-priority` de `docs/openapi.json`): `GET /api/admin/teachers/pending`, `POST /api/admin/teachers/{id}/approve` y `/reject`, `GET /api/admin/priority-users`, `DELETE /api/admin/priority-users/{id}`, `GET /api/admin/users?query=&cursor=` y `PUT /api/admin/users/{id}/role` (#215).

### 09c · Kioscos del QR — `/admin/kioscos`

Sin frame de Figma: mismos componentes que 09b. Ítem **"Kioscos"** en la barra lateral. Contrato en `docs/openapi.json` (`GET /api/admin/kiosks`, `POST /api/admin/kiosks`, `POST /api/admin/kiosks/{id}/revoke`; [ADR 0020](../adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)).

`h1` "Kioscos del QR" y debajo (14 px `--ink-muted`) "Cada tablet del hall muestra el QR de un ascensor con su propia credencial. Si se pierde una, revocala y dá de alta otra." Dos tarjetas:

1. **"Dar de alta un kiosco"**: `select` nativo "Ascensor" (opciones de `GET /api/elevators`: "33 · Independencia 2") y `Button` primary "Dar de alta" (`block={false}`). Error 404 / 409 / otro: mensaje del servidor con `role="alert"`.
   - **Credencial recién creada** (aviso `--accent-tint`): "Kiosco del ascensor 33 dado de alta." · "Copiá la credencial ahora: no la vamos a volver a mostrar. Abrí /qr/33 en la tablet y pegala ahí." · la credencial en un `code` seleccionable · `Button` secondary "Copiar credencial" (al copiar: "Copiada"; si el portapapeles falla: "No pudimos copiar. Seleccioná el texto y copialo a mano.") · `Button` primary "Listo, ya la guardé", que la saca de la pantalla. La credencial vive solo en el estado de la pantalla: al irse o recargar desaparece. La API la devuelve una única vez y en la base queda su hash.
2. **"Kioscos"**: tabla **Ascensor · Tipo · Alta · Última conexión · Estado · Acciones**. Tipo: "Tablet" o "Simulador". Alta y Última conexión en Buenos Aires ("14/10/2026, 15:40"; "Nunca" si no se conectó). Estado: `Chip` accent "Activo" o neutral "Revocado". Acción en los activos: secondary "Revocar" (nombre accesible "Revocar el kiosco del ascensor 33"), con confirmación en la fila: "¿Revocar el kiosco del ascensor 33?" · "Sí, revocar" / "Cancelar" (con el foco). Revocar corta el kiosco en el acto. Vacía: "Todavía no hay kioscos."

Estados de carga y error como 09b.

### 09e · Ascensores fuera de servicio — `/admin/ascensores`

Sin frame de Figma: mismos componentes que 09c. Ítem **"Ascensores"** en la barra lateral. Contrato en `docs/openapi.json` (`GET /api/admin/elevators`, `POST /api/admin/elevators/{code}/out-of-service` y `/in-service`; [turnos.md §8](../reglas/turnos.md#8-ascensor-fuera-de-servicio), #134).

`h1` "Ascensores" y debajo (14 px `--ink-muted`) "Si un ascensor se rompe, marcalo fuera de servicio: sus reservas de hoy se cancelan solas, sin falta, y avisamos a cada persona por mail." Una tarjeta **"Estado de los ascensores"** (`h2`) con la tabla **Ascensor · Núcleo · Reservas próximas · Estado · Acciones**:

- Ascensor = código ("33"); Núcleo = nombre visible ("Independencia 2"); Reservas próximas = `upcomingReservations` (reservas activas de salidas que todavía no pasaron), el número que se cancelaría.
- Estado: `Chip` accent "En servicio" o neutral "Fuera de servicio" (texto, nunca solo color).
- Acciones: en servicio, secondary "Marcar fuera de servicio" (nombre accesible "Marcar fuera de servicio el ascensor 33"); fuera de servicio, secondary "Reactivar" (nombre "Reactivar el ascensor 33"). Ambas con `ConfirmAction` en la fila:
  - Marcar: "¿Marcar el ascensor 33 fuera de servicio? Se cancelan 4 reservas sin falta y se avisa a cada persona." (con 0: "No hay reservas para cancelar."; con 1: "Se cancela 1 reserva sin falta y se avisa a la persona.") · "Sí, marcar" / "Cancelar" (con el foco).
  - Reactivar: "¿Reactivar el ascensor 33? Vuelve a ofrecerse para reservar. Las reservas canceladas no se restauran." · "Sí, reactivar" / "Cancelar".
- El conteo viene de la última lista cargada y puede haber cambiado: al confirmar, el resultado real (`cancelledReservations`) se muestra arriba de la tabla en `role="status"`: "Ascensor 33 fuera de servicio. Se cancelaron 4 reservas." y la lista se recarga.
- Error del servidor: `role="alert"` con el mensaje; con 404 (ascensor borrado) se agrega "Actualizamos la lista." y se recarga.
- Los kioscos de un ascensor fuera de servicio no cambian: el QR se sigue emitiendo, pero nadie tiene reserva para hacer check-in.
- Estados de carga y error como 09c. Sin ascensores: "No hay ascensores para mostrar."

### 09d · Simulador de kiosco — `/admin/simulador`

Para demos y pruebas sin ir al edificio. **Solo existe si `GET /api/admin/kiosks` devuelve `simulatorEnabled: true`** (variable `APP_KIOSK_SIMULATOR_ENABLED` del backend, apagada por default): sin eso no hay ítem en la barra lateral y la ruta muestra "El simulador de kiosco está apagado en este entorno." El front no tiene un flag propio.

1. **Cartel "MODO SIMULADOR"** arriba de todo, siempre visible: banda de ancho completo, pad 12/16, `--radius-md`, fondo `--Status-Media-tint`, borde 2 px `--Status-Media`, texto 16 px / 700 en mayúsculas y `--ink`. Texto y color, nunca solo color.
2. `h1` "Simulador de kiosco" y debajo: "Muestra el QR de un ascensor como la tablet del hall, para probar el check-in desde la app. Usa un kiosco simulado: los check-ins quedan marcados como del simulador."
3. `select` "Ascensor" (mismas opciones que 09c). Al elegir uno, da de alta un kiosco `simulated` de ese ascensor (`POST /api/admin/kiosks` con `simulated: true`) y guarda su credencial **en memoria** mientras dure la pestaña: volver a elegir el mismo ascensor reutiliza el kiosco, no crea otro. Al recargar se pierde y se crea uno nuevo (los viejos se revocan desde 09c).
4. Debajo, el **mismo componente del kiosco** de la pantalla 10 (QR, renovación y estados). Si la credencial fue revocada (401), ofrece `Button` primary "Crear otro kiosco simulado".

El simulador es un cliente más de `GET /api/kiosks/me/qr-token`: no hay endpoints aparte.

## 10 · Kiosco QR — `/qr/:ascensor`

Sin frame de Figma. Pantalla de una tablet o monitor del hall que muestra el QR rotativo del ascensor ([check-in](../reglas/check-in-qr.md)). Sin `TopBar`, sin navegación y **sin selector de vista** (como `/admin`). No pide sesión de usuario: se autentica con la **credencial del kiosco**, que viaja en `Authorization: Bearer` solo en `GET /api/kiosks/me/qr-token` (nunca el JWT de una persona y sin renovación de sesión).

`main` centrado a pantalla completa, pad 32, gap 24.

**Sin credencial guardada:** `h1` "Kiosco del ascensor 33" (`.text-title-lg`); texto "Pegá la credencial que te dieron en el panel de administración al dar de alta este kiosco. Se guarda solo en este dispositivo."; `Input` "Credencial del kiosco" (`type="password"`, `autocomplete="off"`); `Button` primary "Guardar y mostrar el QR". Vacía → "Pegá la credencial del kiosco.". La credencial **no va en la URL ni queda a la vista**: se guarda en `localStorage` del dispositivo (`smartelevate.kiosk.<ascensor>`); si el storage está bloqueado, vale mientras la pestaña siga abierta.

**Con credencial — componente del kiosco** (`KioskQr`, también en 09d):

- `h1` "Ascensor 33" (`.text-title-lg`); QR de hasta **min(70vh, 480 px)**, módulos `--BlackPearl` sobre `--White` (fijos también en modo oscuro: los lectores necesitan contraste), margen de 4 módulos, `--radius-2xl`. `role="img"` con nombre "Código QR del ascensor 33 para hacer check-in". Debajo, `.text-heading` "Escaneá el QR desde la app SmartElevate" y 15 px `--ink-muted` "El código cambia cada pocos segundos: escanealo acá, no le saques foto."
- **Renovación:** pide el siguiente token al llegar a `expiresAt` (los ciclos están alineados al reloj del servidor). El tiempo que falta se mide contra el header `Date` de la respuesta, para que un reloj desfasado de la tablet no la haga pedir de más ni mostrar un QR vencido.
- **Cargando** (`role="status"`): "Cargando el código…".
- **401** (credencial inválida o revocada, code `KIOSK_UNAUTHORIZED`): deja de pedir. Aviso `--danger-tint` (`role="alert"`): título "Este kiosco no está habilitado" y "La credencial no es válida o fue revocada. Pedile una nueva a quien administra SmartElevate." En `/qr` lleva `Button` primary "Borrar credencial", que vuelve al formulario.
- **429**: "Hicimos demasiados pedidos. Reintentamos en {n} s." (`role="status"`) y reintenta según `Retry-After` (30 s si falta).
- **Sin red / error del servidor**: "No pudimos conectarnos. Reintentamos en 10 s." (`role="status"`) y reintenta solo.
- Al pie, `Button` secondary "Borrar credencial" (`block={false}`), con confirmación en el lugar ("¿Borrar la credencial de este dispositivo?" · "Sí, borrar" / "Cancelar" con el foco), porque la tablet está en un lugar público.

- **Credencial de otro ascensor:** la respuesta trae el `elevatorId` de la credencial. Si no coincide con `:ascensor`, deja de pedir y **no muestra el QR** (registraría los check-ins en el otro ascensor). Aviso `--danger-tint` (`role="alert"`): título "Esta credencial es de otro ascensor" y "La credencial es del ascensor 34 y esta pantalla es del ascensor 33. Para no registrar viajes en otro ascensor, no mostramos el QR. Cargá la credencial del ascensor 33." Con la misma acción que el 401 ("Borrar credencial" en `/qr`, "Crear otro kiosco simulado" en 09d).

El ascensor que vale es el de la credencial; el de la URL es el que la pantalla espera. El texto del token es opaco y largo, así que no se muestra, y no hay ingreso manual ([ADR 0021](../adr/0021-check-in-sin-ingreso-manual.md)).


---

## Checklist de accesibilidad para cada pantalla

- Un solo `h1` por pantalla; los títulos de sección con `h2`.
- Todo `input` con `label` asociado; errores con `role="alert"`.
- El estado de congestión, de franja ("Completo") y de validación siempre con texto, no solo color.
- Selectores (edificio, piso, franja, tipo de usuario) navegables con teclado; el elegido con `aria-pressed` o `aria-checked`; los pisos no elegibles con `disabled`.
- Foco visible con `--focus`. Áreas táctiles ≥ 44 × 44 px.
