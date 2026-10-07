# SmartElevate — Pantallas (especificación para Claude Code)

Describe las 9 pantallas del wireframe con medidas, textos y estados, para implementarlas **sin llamar a Figma**. Los tokens, componentes y clases (`--ink`, `radius-lg`, `text-title`, `Button`…) están en `DESIGN-SYSTEM.md`.

**Este archivo describe la versión móvil (390 px).** La disposición en modo Pantalla para laptop y el selector Pantalla / Teléfono están en `VIEW-MODES.md`.

Convenciones: medidas en px; "pad 16" = padding 16 en los cuatro lados, "pad 12/20" = vertical/horizontal; "gap" = separación entre hijos. Todos los datos son **de ejemplo**: alinearlos con el contrato de API del backend antes de conectar.

## Mapa de rutas

| Ruta | Pantalla | Navegación inferior | Acceso |
|---|---|---|---|
| `/login` | 01 Login | no | público |
| `/registro` | 02 Registro | no | público |
| `/recuperar` | 02b Recuperar contraseña | no | público |
| `/recuperar/nueva?token=…` | 02c Contraseña nueva (link del mail) | no | público |
| `/privacidad` | Política de privacidad | no | público |
| `/terminos` | Términos de uso | no | público |
| `/` | 03 Inicio | sí (Inicio) | autenticado |
| `/reservar` | 04 Reservar turno | no (tiene header con volver) | autenticado |
| `/turno/:id` | 05 Turno confirmado | no | autenticado |
| `/check-in` | 06 Check-in (QR) | no | autenticado |
| `/check-in/ok` | 07 Viaje registrado | no | autenticado |
| `/perfil` | 08 Perfil y acceso prioritario | sí (Perfil) | autenticado |
| `/admin` | 09 Panel de administración | — (escritorio) | rol administrador |

Flujo principal: `Login → Inicio → Reservar → Turno confirmado → Check-in → Viaje registrado → Inicio`. La tab "Reservar" lleva a `/reservar`, "Check-in" a `/check-in` y "Perfil" a `/perfil`.

## Reglas de negocio que la UI debe respetar

- Cada franja horaria tiene **capacidad de 10 personas**; al llegar a 10 se muestra "Completo" y no se puede elegir.
- Las franjas de ejemplo son de 5 minutos (7:15 – 7:20, 7:20 – 7:25…), pensadas para la entrada, el recreo y la salida de cada bloque de cursada.
- **Regla de pisos bajos** ([`asignacion.md`](../reglas/asignacion.md)): los trayectos cortos van por escalera, salvo **movilidad reducida**, que queda exenta (los docentes no). Hoy no se reserva con destino en los pisos **1 a 4** (umbral `low_floors_threshold` de la tabla de configuración, [#113](https://github.com/Benji-9/SmartElevate/issues/113)); el "0–4" del wireframe era de ejemplo. La elegibilidad de cada piso y el motivo los manda el servidor; la UI deshabilita esos botones y muestra el motivo, sin hardcodear pisos ni textos.
- El **acceso prioritario** se solicita desde el perfil subiendo un certificado de discapacidad (PDF) o un certificado médico (PDF o imagen); queda en estado "Pendiente de validación" hasta que se valida.
- El **check-in** se hace escaneando el QR que rota en la pantalla dentro de la cabina.
- Los núcleos son **Lima 1, Lima 2, Lima 3, Independencia 1 e Independencia 2** (en botones cortos: "Indep. 1", "Indep. 2"). En el ejemplo, en Lima 3 los ascensores 1–3 sirven los pisos 7–10; la zonificación real por núcleo está por confirmar.
- Autenticación con JWT; registro con email institucional, legajo y contraseña.

> **Ojo (repo):** las franjas de 5 minutos de este wireframe son de ejemplo; la regla vigente es la del ADR 0007 (la franja es una salida de ascensor de ~2 min) y `docs/reglas/`. Ante contradicción manda `docs/reglas/`.

## Tipos sugeridos para los datos de ejemplo

```ts
type CongestionLevel = 'baja' | 'media' | 'alta';
type UserType = 'estudiante' | 'docente'; // declarativo, ver #32
type Core = 'Lima 1' | 'Lima 2' | 'Lima 3' | 'Independencia 1' | 'Independencia 2';

interface Slot { id: string; start: string; end: string; taken: number; capacity: 10 }        // "7:25", "7:30"
interface Turn { id: string; core: Core; elevators: string; floor: number; room?: string; slot: Slot; status: 'confirmado' | 'cancelado' }
interface CoreStatus { core: Core; level: CongestionLevel; occupancy: number /* 0–1 */ }
```

---

## Estructura común (móvil)

> En una laptop esta estructura se ve dentro de un marco de teléfono (modo Teléfono) o se reacomoda a layout de computadora (modo Pantalla); ver `VIEW-MODES.md`.

- Frame de **390 × 844**. Se implementa como contenedor a pantalla completa con `max-width: 390px` centrado en escritorio.
- Esqueleto: `header` opcional → `main` (`padding` de 20 px lateral) → `BottomNav` opcional (fijo abajo).
- `PageHeader` interno: pad 8/20, gap 12, alineado al centro. `BackButton` = círculo de 36 px con fondo `--surface-subtle` y flecha "←" (18 px, 600); área táctil de 44 px. Título en `.text-heading`.

---

## 01 · Login — `/login`

`main`: pad 40/20/24/20, gap 16.

1. **Marca** (columna centrada, gap 12): logo `smartelevate-logo.svg` a 120 px; título `.text-title` centrado "Bienvenido a SmartElevate"; subtítulo 15 px `--ink-muted` centrado "Reservá tu turno de ascensor y llegá a tiempo a clase."
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
2. `Input` × 4: "Nombre y apellido" (ph `Juana Martínez`), "Email institucional" (sufijo fijo `@uade.edu.ar`, ph `jmartinez`), "Legajo" (ph `Ej: 1234567`), "Contraseña" (ph `••••••••`).
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

## Email institucional

Login, Registro y 02b usan el mismo campo ([#162](https://github.com/Benji-9/SmartElevate/issues/162)): `Input` con `suffix="@uade.edu.ar"` (constante `UADE_DOMAIN` en `features/auth/validation.ts`).

- Se escribe solo el usuario; el sufijo `@uade.edu.ar` queda fijo a la derecha dentro del control (`.text-body`, `--ink-muted`) y el email completo se arma al enviar.
- El sufijo entra en `aria-describedby`: el lector anuncia "Email institucional, @uade.edu.ar".
- `type="text"`, `autocomplete="username"`, `autocapitalize="none"`, `spellcheck="false"`.
- Si se pega o autocompleta el email completo con `@uade.edu.ar`, el campo se queda con el usuario. Validación al enviar: vacío → "Ingresá tu email institucional."; con `@` (otro dominio) → "Usá tu email @uade.edu.ar."; con espacios → "Escribilo sin espacios.". Los espacios de los bordes se recortan.
- Es ayuda de UI: el servidor sigue validando el dominio.

## Footer legal y páginas legales

No hay frame en Figma ([#145](https://github.com/Benji-9/SmartElevate/issues/145)).

- **Footer legal** (`LegalFooter`): al final de Login, Registro, 02b, 02c, Perfil y las dos páginas legales. Columna centrada, gap 4, 12 px `--ink-muted`: fila de links (500, `--accent`, sin subrayado, área táctil de 44 px) **Privacidad · Términos de uso · Contacto** (`mailto:` al email del proyecto) y debajo "SmartElevate · Proyecto académico de estudiantes de UADE".
- **Páginas legales** (`/privacidad`, `/terminos`): `PageHeader` con volver + título; "Última actualización" 13 px `--ink-muted`; secciones numeradas con `h2` de 16 px / 600 y texto 14 px con interlineado 1,55. La leyenda obligatoria de la AAIP (Disposición 10/2008) va en un aviso `--accent-tint` dentro de "Tus derechos". En Pantalla usan la columna de 480 px.
- Contenido: la política sigue la Ley 25.326, el Decreto 1558/2001, las normas de la AAIP y el Convenio 108/108+, con los derechos del RGPD; los términos son los de un proyecto académico. **No es asesoramiento legal**: validar con la cátedra ([#24](https://github.com/Benji-9/SmartElevate/issues/24)). Si cambian proveedores, regiones o el tratamiento de datos, actualizar `PrivacyPage.tsx`.

## 03 · Inicio — `/`

`main`: pad 12/20/16/20, gap 16. `BottomNav` con **Inicio** activa.

1. **Saludo** (fila, gap 12): columna con "Hola, Juana" (`.text-title`) y "Lunes 28/9" (14 px `--ink-muted`; la sede queda **pospuesta**: no hay de dónde sacarla, [#86](https://github.com/Benji-9/SmartElevate/issues/86)); a la derecha, avatar circular de 44 px (`--surface-subtle`).
2. **Tarjeta "Próxima clase"** — **pospuesta**: todavía no hay fuente del horario de cursada ([#86](https://github.com/Benji-9/SmartElevate/issues/86)); no implementar por ahora. (pad 18, gap 6, `--radius-xl`, fondo `--accent`): rótulo `.text-overline` "PRÓXIMA CLASE" en `--on-dark-muted`; nombre de la materia 17 px / 700 `--on-accent`: "Seminario de Gestión de Tecnología"; detalle 14 px "Aula 705 · Piso 7 · Lima 3 · 7:45 hs" en `--on-dark-muted`; separación 6; botón interno blanco (pad 10/14, `--radius-sm`, texto 14 px / 600 `--accent`) "Reservar turno para esta clase" → `/reservar`.
3. **Tarjeta "Tu turno"** (pad 16, gap 10, `--radius-xl`, borde 1 px `--line`): fila con "Tu turno" (`.text-card-title`) y `Chip` accent "Confirmado"; línea 15 px / 500 "7:25 – 7:30 · Ascensores 1–3 · Piso 7"; ayuda 13 px `--ink-muted` "Escaneá el QR de la pantalla del ascensor al subir."; `Button` primary "Hacer check-in" → `/check-in`. Si no hay turno, ocultar la tarjeta.
4. **"Congestión ahora"** (gap 10): fila de encabezado con "Congestión ahora" (`.text-card-title`) y "Actualizado hace 1 min" (12 px `--ink-muted`); debajo un `CongestionRow` por núcleo:

| Núcleo | level | value |
|---|---|---|
| Lima 1 | baja | 0.35 |
| Lima 2 | media | 0.6 |
| Lima 3 | alta | 0.9 |
| Independencia 1 | alta | 0.95 |
| Independencia 2 | media | 0.6 |

## 04 · Reservar turno — `/reservar`

`PageHeader` con volver + "Reservar turno". `main`: pad 8/20/20/20, gap 12.

1. Etiqueta "Edificio" (14 px / 600) y **selector de edificio** (pills en fila con wrap, gap 8, pad 8/14, `--radius-pill`, texto 14 px / 500): Lima 1 · Lima 2 · **Lima 3 (seleccionado)** · Indep. 1 · Indep. 2. Seleccionado: fondo `--accent`, texto `--on-accent`; resto: `--surface`, borde 1 px `--border`.
2. Etiqueta "Piso de destino" y **selector de piso**: botones de **50 × 44** (`--radius-sm`, número 15 px / 600) del **0 al 10**, con wrap y gap 8. Piso 7 seleccionado (`--accent`). Pisos **0–4 deshabilitados** para usuarios no prioritarios: fondo `--surface-subtle`, número `--ink-subtle`, `disabled`. Resto: `--surface` con borde `--border`.
3. **Aviso** (pad 12, `--radius-sm`, fondo `--accent-tint`, 12 px) con el motivo que manda el servidor para los pisos deshabilitados (p. ej. "Hasta el piso 4 usá la escalera."). No hardcodear el texto del wireframe.
4. Etiqueta "Franja horaria" y **lista de franjas** (gap 8). Cada franja: fila pad 12/14, gap 12, `--radius-md`, borde 1 px `--line`; a la izquierda hora (15 px / 600) y barra de capacidad (120 × 6, pista `--line`, relleno `--accent`, ancho = `taken/10 × 120`); luego "5/10" (13 px / 500 `--ink-muted`); a la derecha radio de 20 px.

| Franja | Ocupación | Estado |
|---|---|---|
| 7:15 – 7:20 | 10/10 | **Completo**: deshabilitada, hora e indicador atenuados, texto "Completo" en lugar de "10/10" |
| 7:20 – 7:25 | 8/10 | disponible |
| 7:25 – 7:30 | 5/10 | **seleccionada**: borde 2 px `--accent`, fondo `--accent-tint`, radio relleno |
| 7:30 – 7:35 | 2/10 | disponible |

5. `Button` primary con la franja elegida: "Confirmar turno · 7:25". Recomendación: dejarlo fijo al pie mientras el contenido scrollea.

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

3. **Ayuda de check-in** (pad 14, `--radius-md`, `--accent-tint`, 13 px): "Al subir, escaneá el QR que aparece en la pantalla dentro del ascensor para registrar tu viaje."
4. Espacio flexible que empuja los botones al pie.
5. `Button` primary "Ir a check-in" → `/check-in`; `Button` secondary "Cancelar turno", que pide confirmación en un diálogo modal: título "¿Cancelar el turno?" (si el servidor avisa que cuenta como falta, el título es "Si cancelás ahora, cuenta como falta"); botones "Mantener turno" (primary, primero y con el foco al abrir) y "Cancelar turno" (secondary). La acción destructiva nunca lleva el énfasis.

## 06 · Check-in (QR) — `/check-in`

Pantalla **oscura**: fondo `--surface-dark`; barra de estado, título y textos en `--on-dark`. Header con volver (círculo con fondo blanco al 15 %) + "Check-in" en `.text-heading` blanco.

1. **Vista de cámara** (columna centrada, pad 40/20/24/20, gap 20): visor de **250 × 250**, `--radius-2xl`, borde 3 px `--on-dark`, con la imagen de la cámara adentro (en el mockup, un cuadrado de 160 px translúcido); texto 16 px / 600 `--on-dark` centrado "Apuntá la cámara al QR de la pantalla del ascensor"; texto 13 px `--on-dark-muted` "El código rota: escanealo desde dentro de la cabina."
2. **Hoja inferior** (`--surface`, esquinas superiores `--radius-2xl`, pad 20/20/32/20, gap 12): fila con "Tu turno" (`.text-card-title`) y `Chip` accent "7:25 – 7:30"; 14 px `--ink-muted` "Lima 3 · Ascensores 1–3 · Piso 7"; `Button` secondary "Ingresar código manualmente".

Al leer un QR válido → `/check-in/ok`.

## 07 · Viaje registrado — `/check-in/ok`

Sin header ni tabs. `main`: pad 48/20/24/20, gap 16.

1. **Hero** igual al de la 05: título "Viaje registrado"; detalle 15 px `--ink-muted` "Ascensor 2 · Lima 3 · 7:27 hs".
2. **Tarjeta "a tiempo"** (pad 16, gap 4, `--radius-lg`, `--accent-tint`): "Check-in a tiempo" 15 px / 600 `--accent`; debajo, 13 px, el detalle del resultado del check-in ("Subiste dentro de tu franja. ¡Gracias por usar tu turno!") y la franja del turno. La hora y el aula de la clase ("Tu clase empieza a las 7:45 en el Aula 705.") quedan **pospuestas** hasta tener el horario de cursada ([#86](https://github.com/Benji-9/SmartElevate/issues/86)).
3. **Encuesta de espera** (pad 16, gap 12, `--radius-lg`, borde `--line`): pregunta 15 px / 600 "¿Cuánto esperaste el ascensor?"; 4 opciones de igual ancho (pad 10/4, `--radius-sm`, 13 px / 500): "< 2 min", "2–5", "5–10", "> 10" (opción elegida: borde `--accent`, fondo `--accent-tint`, texto `--accent`); nota 12 px `--ink-muted` "Nos ayuda a medir la congestión real."
4. Espacio flexible; `Button` primary "Volver al inicio" → `/`.

> La encuesta de espera es una propuesta del wireframe, sin decisión confirmada del equipo: implementarla como opcional.

## 08 · Perfil y acceso prioritario — `/perfil`

`PageHeader` sin volver, título "Mi perfil". `main`: pad 8/20/20/20, gap 16. `BottomNav` con **Perfil** activa.

1. **Cabecera** (fila, gap 14): avatar de 60 px; columna (gap 4): "Juana Martínez" (18 px / 700), `Chip` tone `alumnos` "Estudiante", "Legajo 1234567" (13 px `--ink-muted`), "jmartinez@uade.edu.ar" (13 px `--ink-muted`). El chip cambia de tono con el tipo de usuario.
2. **Tarjeta "Acceso prioritario"** (pad 16, gap 12, `--radius-lg`, borde `--line`):
   - fila: "Acceso prioritario" (`.text-card-title`) + `Chip` accent "Pendiente de validación";
   - texto 13 px `--ink-muted`: "Si tenés movilidad reducida, subí tu certificado de discapacidad o un certificado médico para habilitar turnos prioritarios (incluye los pisos bajos)."
   - **Zona de carga** (pad 20, gap 6, `--radius-sm`, borde discontinuo 1,5 px `--accent`, fondo `--accent-tint`, contenido centrado): ícono de 28 px, "Subir certificado" 14 px / 600 `--accent`, "PDF o imagen" 12 px `--ink-muted`.
   - **Archivo cargado** (fila pad 10/12, gap 10, `--radius-sm`, `--surface-subtle`): ícono de archivo, nombre 13 px / 500 (`certificado_discapacidad.pdf`), acción "Quitar" 12 px / 500 `--accent`.
   - Estados del chip: "Pendiente de validación" → validado → rechazado (definir con el backend).
3. **Lista de ajustes** (`--radius-lg`, borde `--line`): filas pad 14/16, 15 px / 500, flecha "→" a la derecha en `--ink-muted`, divisor `--line` entre filas: "Mis viajes", "Notificaciones", "Cerrar sesión".

## 09 · Panel de administración — `/admin`

Escritorio **1440 × 900**. Layout en dos columnas.

**Barra lateral** (248 px, fondo `--surface-dark`, pad 28/16, gap 6): "SmartElevate" (20 px / 700 `--on-dark`), "Panel UADE" (12 px `--on-dark-muted`), separación 20 y navegación (cada ítem pad 10/12, gap 10, `--radius-sm`, ícono de 18 px + texto 14 px): **Dashboard** (activo: fondo blanco al 12 %, texto 600 `--on-dark`), Turnos, Núcleos y ascensores, Usuarios prioritarios (cola de docentes a validar, spec pendiente en [#140](https://github.com/Benji-9/SmartElevate/issues/140)), Reportes (`--on-dark-muted`).

**Contenido** (pad 32, gap 24):

1. **Encabezado**: a la izquierda "Congestión y uso de ascensores" (`.text-title-lg`) y "Lunes 28/9 · Sede Lima · Turno mañana (7:00–12:15)" (14 px `--ink-muted`); a la derecha los filtros de período, sede y **turno de cursada** ("Turno mañana"). Los turnos y sus horarios los manda el servidor (tabla de configuración, ver [`kpis.md`](../reglas/kpis.md#filtros)).
2. **4 tarjetas KPI** en fila (gap 16, cada una pad 20, gap 6, `--radius-lg`, borde `--line`): etiqueta 13 px `--ink-muted`, cifra `.text-kpi`, detalle 12 px / 500 `--accent`.

| Etiqueta | Cifra | Detalle |
|---|---|---|
| Espera promedio | 4,1 min | comparación con la línea base de [`kpis.md`](../reglas/kpis.md) (% que espera 5–10 min); el "−38 %" del wireframe era de ejemplo |
| Turnos reservados | 1.284 | hoy |
| Check-ins realizados | 87% | de los turnos reservados |
| Ocupación promedio | 7,6 / 10 | personas por franja |

3. **Fila de dos tarjetas** (gap 16, mismo ancho):
   - **Gráfico "Reservas por franja (turno mañana)"** (pad 20, gap 16, `--radius-lg`, borde `--line`): título `.text-card-title`; barras verticales de 28 px de ancho, `--radius` 6, área de 240 px de alto, alineadas al fondo, con la hora debajo (11 px `--ink-muted`). Datos de ejemplo (alto en px): 7:15 → 120 · 7:25 → 170 · 7:35 → 150 · 7:45 → 60 · 9:30 → 90 · 9:45 → 140 · 11:30 → 80 · 11:45 → 160. Barras de las franjas más cargadas (≥ 140) en `--accent` sólido, el resto en `--accent` al 45 %.
   - **Tabla "Estado por núcleo"** (pad 20, `--radius-lg`, borde `--line`): columnas **Núcleo · Reservas · Ocupación · Espera · Estado**; encabezado 13 px / 600 `--ink-muted`; filas 13 px con pad 12/0 y divisor `--line`. La columna Estado es un `Chip` con tono `baja` / `media` / `alta`.

| Núcleo | Reservas | Ocupación | Espera | Estado |
|---|---|---|---|---|
| Lima 1 | 212 | 58% | 2,1 min | Baja |
| Lima 2 | 298 | 71% | 3,4 min | Media |
| Lima 3 | 356 | 86% | 5,2 min | Alta |
| Indep. 1 | 241 | 90% | 6,0 min | Alta |
| Indep. 2 | 177 | 66% | 3,0 min | Media |

4. Nota al pie 12 px `--ink-muted`: "* Datos de ejemplo (ficticios) para el wireframe." (quitar al conectar datos reales).

---

## Checklist de accesibilidad para cada pantalla

- Un solo `h1` por pantalla; los títulos de sección con `h2`.
- Todo `input` con `label` asociado; errores con `role="alert"`.
- El estado de congestión, de franja ("Completo") y de validación siempre con texto, no solo color.
- Selectores (edificio, piso, franja, tipo de usuario) navegables con teclado; el elegido con `aria-pressed` o `aria-checked`; los pisos no elegibles con `disabled`.
- Foco visible con `--focus`. Áreas táctiles ≥ 44 × 44 px.
