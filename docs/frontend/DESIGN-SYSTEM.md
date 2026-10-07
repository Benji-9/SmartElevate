# SmartElevate — Design System (referencia para Claude Code)

Fuente única de estilos para construir el frontend **sin consultar Figma**. Complementa a `SCREENS.md` (pantallas en versión móvil) y a `VIEW-MODES.md` (selector Pantalla / Teléfono para mostrar el MVP en una laptop).
Stack: **React + Vite**, mobile-first (390 px), con una vista de Pantalla para laptop y un panel admin de escritorio (1440 px). Idioma de la interfaz: español rioplatense (voseo).

> Si algo de este documento contradice a un frame de Figma, **manda este documento**: los tokens acá pasaron por una revisión de contraste (WCAG AA) que el wireframe no tiene.

---

## 1. Cómo empezar

1. Copiá el bloque de la sección 3 a `src/styles/tokens.css` y el de la sección 4 a `src/styles/type.css`; importalos en `main.tsx`.
2. Cargá Inter (400, 500, 600, 700) en `index.html`:
   ```html
   <link rel="preconnect" href="https://fonts.googleapis.com">
   <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
   ```
3. Creá un componente React por cada uno de la sección 6 (`Button`, `Input`, `Chip`, `CongestionRow`, `BottomNav`) con las clases de la sección 7.
4. Para que la app se pueda mostrar en una laptop, seguí `VIEW-MODES.md`: layouts con container queries (nunca `@media` de ancho) y selector Pantalla / Teléfono.
5. No uses hex sueltos ni opacidades sueltas en componentes: solo variables `var(--…)` de este archivo.

---

## 2. Marca en breve

- **Personalidad:** institucional y tranquila. Mucho blanco, un solo azul de acción y color de semáforo únicamente cuando hay un estado que comunicar.
- **Azul de acción `AstronautBlue` `#00405c`:** es el azul del logotipo de UADE. **`BlackPearl` `#061b2b`:** Pantone 296C, color institucional de UADE. Ambos vienen del Manual de Marca.
- **Semáforo de congestión:** verde `Status-Baja` `#016836` (Costa Argentina), amarillo `Status-Media` `#f7a700` (Ingeniería), rojo `Status-Alta` `#cf0a2c`. Nunca solo color: siempre la palabra "Baja", "Media" o "Alta".
- **Tipo de usuario:** lima `User-Alumnos` `#c3d500`, naranja `User-Docentes` `#ff5100`. Solo como punto o badge.
- **No usar:** colores de otras facultades del manual, ni los azules brillantes del logo de SmartElevate como color de interfaz, ni sombras (se separa con bordes), ni emojis.
- **Tipografía:** Inter es una elección del equipo; el manual de marca no define una tipografía. Se cambia en un solo lugar (`--font-sans`).

---

## 3. Tokens (CSS custom properties)

```css
:root {
  /* Marca (Manual de Marca UADE) */
  --AstronautBlue: #00405c;
  --White: #ffffff;
  --BlackPearl: #061b2b;

  /* Semánticos */
  --accent: var(--AstronautBlue);
  --on-accent: var(--White);
  --focus: var(--AstronautBlue);
  --surface: var(--White);
  --surface-subtle: #061b2b0a;
  --accent-tint: #00405c12;
  --accent-tint-strong: #00405c1a;
  --ink: var(--BlackPearl);
  --ink-muted: #061b2bb3;
  --ink-subtle: #061b2b99;
  --border: #061b2b80;
  --line: #061b2b1f;
  --surface-dark: var(--BlackPearl);
  --on-dark: var(--White);
  --on-dark-muted: #ffffffa6;

  /* Estados de congestión */
  --Status-Baja: #016836;
  --Status-Media: #f7a700;
  --Status-Alta: #cf0a2c;
  --Status-Baja-tint: #01683624;
  --Status-Media-tint: #f7a70024;
  --Status-Alta-tint: #cf0a2c24;

  /* Tipo de usuario */
  --User-Alumnos: #c3d500;
  --User-Docentes: #ff5100;
  --User-Alumnos-tint: #c3d50040;
  --User-Docentes-tint: #ff510040;

  /* Espaciado */
  --space-4: 4px;
  --space-6: 6px;
  --space-8: 8px;
  --space-10: 10px;
  --space-12: 12px;
  --space-14: 14px;
  --space-16: 16px;
  --space-20: 20px;
  --space-24: 24px;
  --space-32: 32px;
  --space-40: 40px;
  --space-48: 48px;

  /* Radios */
  --radius-xs: 4px;
  --radius-sm: 10px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 18px;
  --radius-2xl: 24px;
  --radius-pill: 999px;

  /* Tipografía */
  --font-sans: Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif;

  /* Layout (ver VIEW-MODES.md) */
  --phone-w: 390px;
  --phone-h: 844px;
  --phone-bezel: 10px;
  --phone-scale: 1;
  --container-max: 1120px;
  --topbar-h: 64px;
}
```

### Colores (con reglas de uso)

| Token | Valor | Uso |
|---|---|---|
| `--AstronautBlue` | `#00405c` | Color de acción de SmartElevate y azul del logotipo de UADE: botón primario, ítem seleccionado, links, tab activa y foco. Texto en AstronautBlue sobre `surface` (11,1:1); sobre él va `on-accent`. |
| `--White` | `#ffffff` | Fondo de pantallas, cards y hojas inferiores; texto e íconos sobre `AstronautBlue` y `BlackPearl`. |
| `--BlackPearl` | `#061b2b` | Color institucional de UADE (Pantone 296C). Texto principal sobre `surface` (17,5:1), barra lateral del panel admin y fondo de la cámara de check-in. Los grises se piden por token (`ink-muted`, `border`, `line`…), no con opacidades sueltas. |
| `--Status-Baja` | `#016836` | Congestión baja (verde Costa Argentina, paleta UADE). Barras, puntos y fondos tenues; siempre con la palabra "Baja". Legible como texto sobre `surface` (6,9:1). |
| `--Status-Media` | `#f7a700` | Congestión media (amarillo de Ingeniería, paleta UADE). Solo barras, puntos y fondos tenues, siempre con la palabra "Media": como texto sobre `surface` no llega (2,0:1). |
| `--Status-Alta` | `#cf0a2c` | Congestión alta y errores (rojo institucional UADE). Barras, puntos, bordes de error y mensajes de error sobre `surface` (5,6:1); siempre con la palabra o el mensaje. |
| `--User-Alumnos` | `#c3d500` | Acento del tipo de usuario Estudiante (color Alumnos de UADE): punto del selector de registro y badge del perfil. Nunca como texto sobre `surface` (1,6:1). |
| `--User-Docentes` | `#ff5100` | Acento del tipo de usuario Docente (color Docentes de UADE): punto y badge. No usar como texto sobre `surface` (3,3:1). |
| `--accent` | `{AstronautBlue}` | Alias semántico de `AstronautBlue`: relleno de botón primario, selección, links y tab activa. |
| `--on-accent` | `{White}` | Etiqueta e ícono sobre un relleno `accent` (11,1:1). |
| `--focus` | `{AstronautBlue}` | Anillo de foco de teclado: contorno sólido de 2px con 2px de separación; 11,1:1 sobre `surface` y 9,9:1 sobre `accent-tint`. |
| `--surface` | `{White}` | Fondo de página, cards y hojas. Sobre él leen `ink`, `ink-muted`, `ink-subtle` y `accent`. |
| `--surface-subtle` | `#061b2b0a` | Fondo de grupos, pistas de barras deshabilitadas y controles inactivos (BlackPearl al 4%). El texto `ink-muted` sigue en 6,1:1 sobre él. |
| `--accent-tint` | `#00405c12` | Fondo de avisos informativos y ayudas (AstronautBlue al 7%); texto `ink` o `accent` encima (9,9:1). |
| `--accent-tint-strong` | `#00405c1a` | Fondo de chips de acento y del círculo de éxito (AstronautBlue al 10%); texto `accent` encima. |
| `--ink` | `{BlackPearl}` | Texto principal e íconos sobre `surface`, `surface-subtle` y los `*-tint` (17,5:1 sobre `surface`). |
| `--ink-muted` | `#061b2bb3` | Texto secundario, etiquetas de campo y tabs inactivas sobre `surface` y `surface-subtle` (BlackPearl al 70%: 6,6:1 y 6,1:1). Desde 11px. |
| `--ink-subtle` | `#061b2b99` | Placeholders y texto deshabilitado sobre `surface` (BlackPearl al 60%: 4,7:1). No usar para información que el usuario necesite leer. |
| `--border` | `#061b2b80` | Contorno de inputs, opciones y controles (BlackPearl al 50%: 3,4:1 sobre `surface`, como exige WCAG 1.4.11 para controles). |
| `--line` | `#061b2b1f` | Separadores y contorno de cards, solo decorativos (BlackPearl al 12%); nunca el único borde de un control. |
| `--surface-dark` | `{BlackPearl}` | Fondo oscuro: cámara de check-in y barra lateral del panel admin. Sobre él leen `on-dark` y `on-dark-muted`. |
| `--on-dark` | `{White}` | Texto e íconos sobre `surface-dark` (17,5:1). |
| `--on-dark-muted` | `#ffffffa6` | Texto secundario sobre `surface-dark` (blanco al 65%: 7,9:1). |
| `--Status-Baja-tint` | `#01683624` | Fondo de la pill de estado "Baja" (verde al 14%); texto `ink` encima (14,1:1). |
| `--Status-Media-tint` | `#f7a70024` | Fondo de la pill de estado "Media" (amarillo al 14%); texto `ink` encima (15,9:1). |
| `--Status-Alta-tint` | `#cf0a2c24` | Fondo de la pill de estado "Alta" (rojo al 14%); texto `ink` encima (13,7:1). |
| `--User-Alumnos-tint` | `#c3d50040` | Fondo del badge de tipo de usuario Estudiante (lima al 25%); texto `ink` encima (15,3:1). |
| `--User-Docentes-tint` | `#ff510040` | Fondo del badge de tipo de usuario Docente (naranja al 25%); texto `ink` encima (12,9:1). |

**Pares de contraste ya verificados:** `ink` sobre `surface` 17,5:1 · `ink-muted` 6,6:1 · `ink-subtle` 4,7:1 · `accent` sobre `surface` 11,1:1 · `on-accent` sobre `accent` 11,1:1 · `border` sobre `surface` 3,4:1. No uses `Status-Media`, `User-Alumnos` ni `User-Docentes` como color de texto sobre blanco.

### Espaciado y radios

| Token | Valor | Uso |
|---|---|---|
| `--space-4` | 4px | Separación mínima: entre título y subtítulo dentro de un bloque. |
| `--space-6` | 6px | Entre etiqueta y campo; entre punto y texto de un chip. |
| `--space-8` | 8px | Entre opciones en fila (chips, pisos, franjas) y padding lateral de tab. |
| `--space-10` | 10px | Padding horizontal de chip y separación de filas de congestión. |
| `--space-12` | 12px | Separación dentro de una card y entre ícono y texto. |
| `--space-14` | 14px | Padding horizontal de campos y filas seleccionables. |
| `--space-16` | 16px | Padding de card y separación entre bloques de una pantalla. |
| `--space-20` | 20px | Padding lateral de toda pantalla móvil y horizontal de botón. |
| `--space-24` | 24px | Padding de tarjetas grandes, base de la barra de tabs y márgenes de la portada. |
| `--space-32` | 32px | Padding de panel y de sección del panel admin. |
| `--space-40` | 40px | Margen superior de pantallas de bienvenida y alto de tile en la portada. |
| `--space-48` | 48px | Margen superior de pantallas de confirmación y ancho de tile en la portada. |
| `--radius-xs` | 4px | Barras de congestión y de capacidad. |
| `--radius-sm` | 10px | Inputs, opciones seleccionables, botones de piso y tiles. |
| `--radius-md` | 12px | Botones y franjas horarias. |
| `--radius-lg` | 16px | Cards de contenido, tablas y KPIs. |
| `--radius-xl` | 18px | Tarjetas destacadas de Inicio (próxima clase, turno activo). |
| `--radius-2xl` | 24px | Hojas inferiores, visor QR y paneles. |
| `--radius-pill` | 999px | Chips, pills de estado y badges. |

---

## 4. Tipografía

Familia: `Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif` (`--font-sans`).

```css
.text-kpi { font: 700 30px/36px var(--font-sans); }
.text-title-lg { font: 700 26px/32px var(--font-sans); }
.text-title { font: 700 24px/30px var(--font-sans); }
.text-heading { font: 700 20px/26px var(--font-sans); }
.text-heading-sm { font: 700 18px/24px var(--font-sans); }
.text-card-title { font: 700 16px/22px var(--font-sans); }
.text-button { font: 600 16px/24px var(--font-sans); }
.text-body { font: 400 15px/22px var(--font-sans); }
.text-body-strong { font: 600 15px/22px var(--font-sans); }
.text-body-sm { font: 400 14px/20px var(--font-sans); }
.text-body-sm-strong { font: 600 14px/20px var(--font-sans); }
.text-label { font: 500 13px/18px var(--font-sans); }
.text-caption { font: 400 12px/16px var(--font-sans); }
.text-caption-strong { font: 500 12px/16px var(--font-sans); }
.text-overline { font: 600 11px/14px var(--font-sans); letter-spacing: 0.08em; text-transform: uppercase; }
```

| Clase | Tamaño / interlínea | Peso | Uso |
|---|---|---|---|
| `.text-kpi` | 30px / 36px | 700 | Cifra grande de un indicador del panel admin. |
| `.text-title-lg` | 26px / 32px | 700 | Título de página del panel admin. |
| `.text-title` | 24px / 30px | 700 | Título de pantalla móvil (bienvenida, saludo, confirmaciones). |
| `.text-heading` | 20px / 26px | 700 | Título del header de una pantalla interna. |
| `.text-heading-sm` | 18px / 24px | 700 | Nombre en el perfil y curso en la tarjeta de próxima clase. |
| `.text-card-title` | 16px / 22px | 700 | Título de card y de sección dentro de una pantalla. |
| `.text-button` | 16px / 24px | 600 | Etiqueta de botón. |
| `.text-body` | 15px / 22px | 400 | Texto de párrafo, valores de campo y filas de franja. |
| `.text-body-strong` | 15px / 22px | 600 | Valor destacado dentro de una fila o franja horaria. |
| `.text-body-sm` | 14px / 20px | 400 | Texto secundario en cards y subtítulos. |
| `.text-body-sm-strong` | 14px / 20px | 600 | Etiqueta de opción seleccionable y de fila de tabla. |
| `.text-label` | 13px / 18px | 500 | Etiqueta de campo, nivel de congestión y ayudas. |
| `.text-caption` | 12px / 16px | 400 | Aclaraciones, marcas de tiempo y notas al pie. |
| `.text-caption-strong` | 12px / 16px | 500 | Texto de chip y pill de estado. |
| `.text-overline` | 11px / 14px | 600 | Rótulo corto en mayúsculas sobre un título; también etiqueta de tab. |

---

## 5. De Figma a tokens (si ves opacidades sueltas)

Los frames de Figma usan BlackPearl con opacidades. Al implementar, sustituí siempre por el token:

| En Figma (BlackPearl a…) | Token |
|---|---|
| Texto 100 % / 75–85 % | `--ink` |
| Texto 55–70 % (secundario, etiquetas, captions) | `--ink-muted` |
| Texto 45 % (placeholder) y deshabilitado | `--ink-subtle` |
| Relleno 4–8 % (grupos, archivos, pistas) | `--surface-subtle` (barras: `--line`) |
| Borde 12–15 % | `--line` (cards, divisores) |
| Borde 20 % (inputs, opciones) | `--border` (más oscuro a propósito: 3:1) |
| AstronautBlue 4–10 % (avisos, chips) | `--accent-tint` o `--accent-tint-strong` |
| Blanco 60–70 % sobre oscuro | `--on-dark-muted` |

---

## 6. Componentes

Todos aceptan `className`. Las clases (prefijo `se-`) están en la sección 7.

| Componente | Props | Notas |
|---|---|---|
| `Button` | `variant: 'primary' \| 'secondary'` (default `primary`), `block?: boolean` (default `true`), + atributos de `<button>` | Un solo `primary` por vista. Etiqueta con verbo: "Ingresar", "Confirmar turno · 7:25". Alto mínimo 48 px. `disabled` usa `surface-subtle` + `ink-subtle`. |
| `Input` | `label?: string`, `error?: string`, + atributos de `<input>` | Etiqueta arriba (13 px, 500). Con `error`: borde y mensaje en `Status-Alta`, `aria-invalid`, mensaje con `role="alert"`. El placeholder es un ejemplo, no reemplaza la etiqueta. |
| `Chip` | `tone?: 'accent' \| 'baja' \| 'media' \| 'alta' \| 'alumnos' \| 'docentes'` (default `accent`) | `accent`: sin punto, para estados de reserva ("Confirmado", "Pendiente de validación"). Los demás llevan punto de color + texto en `ink`. |
| `CongestionRow` | `label: string`, `level: 'baja' \| 'media' \| 'alta'`, `value: number` (0–1) | Nombre del núcleo · barra de 90 × 8 px · nivel escrito (48 px de ancho). Apilar una por núcleo. |
| `BottomNav` | `items: { id, label, icon? }[]`, `active: string`, `onSelect?: (id) => void` | Cuatro destinos: Inicio, Reservar, Check-in, Perfil. Activa en `accent`, resto en `ink-muted`. Sin `icon` muestra un cuadrado gris de 24 px (marcador: **el set de íconos aún no está elegido**). |

### Patrones que no son componente todavía

Se implementan dentro de cada pantalla (detalle en `SCREENS.md`); conviene extraerlos a componentes cuando se repitan: `BackButton`, `PageHeader`, `SelectablePill` (edificio y tipo de usuario), `FloorButton`, `SlotOption`, `SuccessHero`, `InfoNotice`, `UploadDropzone`, `KpiCard`, `DataTable`, `BarChart`, `BottomSheet`.

> La barra de estado del teléfono (9:41, señal, batería) es decoración: **solo se dibuja dentro del marco de teléfono** del modo Teléfono (ver `VIEW-MODES.md`); nunca en Pantalla ni en un celular real.

---

## 7. CSS de los componentes

```css
body { margin: 0; font-family: var(--font-sans); background: var(--surface); color: var(--ink); }

.se-btn { font-family: inherit; font-size: 16px; line-height: 24px; font-weight: 600; min-height: 48px; padding: 0 var(--space-20); box-sizing: border-box; display: inline-flex; align-items: center; justify-content: center; border-radius: var(--radius-md); border: 1.5px solid var(--accent); cursor: pointer; }
.se-btn-block { width: 100%; }
.se-btn-primary { background: var(--accent); color: var(--on-accent); }
.se-btn-secondary { background: var(--surface); color: var(--accent); }
.se-btn:disabled { background: var(--surface-subtle); color: var(--ink-subtle); border-color: transparent; cursor: not-allowed; }
.se-btn:focus-visible, .se-input:focus-visible, .se-nav-item:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }

.se-field { display: flex; flex-direction: column; gap: var(--space-6); }
.se-field-label { font-size: 13px; line-height: 18px; font-weight: 500; color: var(--ink-muted); }
.se-input { font-family: inherit; font-size: 15px; line-height: 22px; color: var(--ink); height: 48px; padding: 0 var(--space-14); box-sizing: border-box; width: 100%; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-sm); }
.se-input::placeholder { color: var(--ink-subtle); }
.se-input:focus { border-color: var(--accent); }
.se-input-error { border-color: var(--Status-Alta); }
.se-field-error { margin: 0; font-size: 13px; line-height: 18px; font-weight: 500; color: var(--Status-Alta); }

.se-chip { display: inline-flex; align-items: center; gap: var(--space-6); padding: var(--space-4) var(--space-10); border-radius: var(--radius-pill); font-size: 12px; line-height: 16px; font-weight: 500; color: var(--ink); }
.se-chip-accent { background: var(--accent-tint-strong); color: var(--accent); }
.se-chip-dot { width: 8px; height: 8px; border-radius: var(--radius-pill); flex: none; }
.se-chip-baja { background: var(--Status-Baja-tint); } .se-chip-baja .se-chip-dot { background: var(--Status-Baja); }
.se-chip-media { background: var(--Status-Media-tint); } .se-chip-media .se-chip-dot { background: var(--Status-Media); }
.se-chip-alta { background: var(--Status-Alta-tint); } .se-chip-alta .se-chip-dot { background: var(--Status-Alta); }
.se-chip-alumnos { background: var(--User-Alumnos-tint); } .se-chip-alumnos .se-chip-dot { background: var(--User-Alumnos); }
.se-chip-docentes { background: var(--User-Docentes-tint); } .se-chip-docentes .se-chip-dot { background: var(--User-Docentes); }

.se-cong { display: flex; align-items: center; gap: var(--space-10); }
.se-cong-label { flex: 1; font-size: 14px; line-height: 20px; font-weight: 500; color: var(--ink); }
.se-cong-track { width: 90px; height: 8px; border-radius: var(--radius-xs); background: var(--line); overflow: hidden; flex: none; }
.se-cong-fill { height: 100%; border-radius: var(--radius-xs); }
.se-cong-baja { background: var(--Status-Baja); } .se-cong-media { background: var(--Status-Media); } .se-cong-alta { background: var(--Status-Alta); }
.se-cong-level { width: 48px; font-size: 13px; line-height: 18px; font-weight: 600; color: var(--ink); flex: none; }

.se-nav { display: flex; padding: var(--space-10) var(--space-8) calc(var(--space-24) + env(safe-area-inset-bottom, 0px)); background: var(--surface); border-top: 1px solid var(--line); }
.se-nav-item { flex: 1; min-height: 44px; display: flex; flex-direction: column; align-items: center; gap: var(--space-4); background: none; border: 0; font-family: inherit; color: var(--ink-muted); cursor: pointer; border-radius: var(--radius-sm); }
.se-nav-icon { width: 24px; height: 24px; border-radius: 7px; background: var(--line); }
.se-nav-label { font-size: 11px; line-height: 14px; font-weight: 500; }
.se-nav-active { color: var(--accent); }
.se-nav-active .se-nav-icon { background: var(--accent); }
.se-nav-active .se-nav-label { font-weight: 600; }
```

Foco de teclado global: `outline: 2px solid var(--focus); outline-offset: 2px;` en todo control interactivo. Área táctil mínima de 44 × 44 px (el botón de volver mide 36 px visibles: darle 44 px de área con `padding`/pseudo-elemento).

---

## 8. Voz y contenido

- Voseo y oraciones cortas: "Reservá tu turno", "Escaneá el QR", "¿Olvidaste tu contraseña?".
- Botones: verbo primero, solo la primera letra en mayúscula.
- Franjas: `7:25 – 7:30` (raya media con espacios). Hora de clase: `7:45 hs`. Fechas: `Lunes 28/9`. Ubicación separada por ` · `: `Aula 705 · Piso 7 · Lima 3`.
- Números: coma decimal y punto de miles (`4,1 min`, `1.284`).
- Estados siempre con palabra: Baja · Media · Alta · Completo · Confirmado · Pendiente de validación.
- Signo de exclamación solo para confirmar algo logrado ("¡Turno confirmado!").

---

## 9. Assets

Los tres SVG de SmartElevate tienen el texto convertido a trazos (Inter Medium) y, con `prefers-color-scheme: dark`, cambian el azul oscuro por blanco y aclaran el azul, así que sirven sobre fondos claros y oscuros sin cajas blancas. Se reconstruyeron a partir de `Icono SmartElevate.jpg` (ya no está en el repo).

| Archivo | Uso |
|---|---|
| `frontend/src/assets/smartelevate-logo.svg` | Marca + logotipo de SmartElevate, vertical, **fondo transparente**. Login: 120 px de alto. Sin recolorear ni estirar. |
| `frontend/src/assets/smartelevate-logo-horizontal.svg` | Ícono + logotipo en una línea, transparente. Para el `TopBar` (~32 px de alto). |
| `frontend/src/assets/smartelevate-mark.svg` | Solo el ícono (ascensor con personas), transparente. Favicon o espacios chicos. |
| `frontend/src/assets/uade-logo.svg` (`UADE_id7RUMB-t-_1.svg`) | Logotipo de UADE, un solo color (`AstronautBlue`), **fondo transparente**; con `prefers-color-scheme: dark` pasa a blanco, igual que los de SmartElevate. Preferido. Más chico que la marca de SmartElevate. |
| `UADE_id7RUMB-t-_0.png` | Mismo logotipo en PNG transparente. Solo donde no pueda usarse SVG. |

---

## 10. Referencia a Figma (solo si hace falta)

Archivo: **SmartElevate — Wireframes v0** · https://www.figma.com/design/KkawjzarpaoL6xIrTntoMC

Todo lo necesario para implementar está en este documento y en `SCREENS.md`. **Evitá llamar al MCP de Figma** salvo para una duda visual puntual (cada llamada cuenta contra el límite). Si hace falta, pedí un solo frame por su ID:

| Elemento | Node ID |
|---|---|
| 01 Login | `3:152` |
| 02 Registro | `3:181` |
| 03 Inicio | `3:227` |
| 04 Reservar turno | `3:302` |
| 05 Turno confirmado | `3:388` |
| 06 Check-in (QR) | `3:428` |
| 07 Viaje registrado | `3:456` |
| 08 Perfil y acceso prioritario | `3:491` |
| 09 Panel admin | `3:549` |
| Componentes (StatusBar `3:114`, Button/Primary `3:121`, Button/Secondary `3:124`, Input `3:127`, Chip `3:132`, BottomNav `3:135`) | `3:111` |
