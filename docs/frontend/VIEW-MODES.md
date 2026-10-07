# SmartElevate — Modos de vista: Pantalla y Teléfono

El MVP se va a mostrar en una laptop. Este documento define cómo la misma app se puede ver de dos formas y cómo cambiar entre ellas:

- **Pantalla**: layout de computadora, a todo el ancho de la ventana.
- **Teléfono**: la app en su versión móvil (390 × 844) dentro de un marco de celular centrado en la ventana.

Complementa a `DESIGN-SYSTEM.md` (tokens y componentes) y `SCREENS.md` (las pantallas en versión móvil). **Las pantallas móviles de `SCREENS.md` no cambian**: acá se agrega el marco, el selector y cómo se reacomoda cada pantalla en modo Pantalla.

> En Figma solo están dibujadas la versión móvil y el panel admin. El layout de Pantalla de las pantallas de usuario está definido únicamente en este documento.

---

## 1. Regla de oro: los layouts responden al ancho del contenedor, no de la ventana

Si se usaran `@media (min-width: …)`, el modo Teléfono en una laptop se vería como escritorio, porque la ventana sigue siendo ancha. Por eso todos los reacomodos se hacen con **container queries** sobre un contenedor llamado `app`:

```css
.app-shell { container: app / inline-size; }

/* móvil por defecto; escritorio solo si el contenedor mide 900 px o más */
@container app (min-width: 900px) {
  .home-grid { grid-template-columns: 7fr 5fr; }
}
```

Con una sola hoja de estilos se cubren los tres casos:

| Situación | Ancho del contenedor `app` | Resultado |
|---|---|---|
| Celular real | ancho de la ventana (< 900) | layout móvil, a pantalla completa |
| Laptop, modo **Pantalla** | ancho de la ventana (≥ 900) | layout de computadora |
| Laptop, modo **Teléfono** | 390 (fijo, dentro del marco) | layout móvil, en el marco |

Prohibido usar `@media` de ancho para layout de pantallas (sí se pueden usar `@media (hover: hover)` y `prefers-reduced-motion`).

---

## 2. Cuándo aparece cada cosa

| Ancho de la ventana | Selector de vista | Marco de teléfono | Layout |
|---|---|---|---|
| **≥ 1024 px** | visible | solo en modo Teléfono | Pantalla (por defecto) o Teléfono |
| **< 1024 px** | oculto | nunca | según el ancho de la ventana (≥ 900: escritorio; menos: móvil) |

- **Modo por defecto:** `pantalla` si la ventana mide ≥ 1024 px; en menos, no hay selector ni marco.
- **Orden de prioridad para decidir el modo:** parámetro `?vista=telefono|pantalla` de la URL → valor guardado en `localStorage` (`se:viewMode`) → por defecto.
- **En `/admin` no hay selector:** el panel es solo de escritorio (ver sección 7).
- Cambiar de modo **no debe reiniciar** la pantalla, la ruta ni el estado de los formularios (ver sección 4).

---

## 3. Selector de vista (`ViewModeToggle`)

Control segmentado de dos opciones, fijo en la esquina inferior derecha de la ventana, fuera de la app.

| Propiedad | Valor |
|---|---|
| Posición | `position: fixed; right: var(--space-24); bottom: var(--space-24); z-index: 1000` |
| Contenedor | fondo `--surface`, borde 1 px `--border`, `--radius-pill`, pad 4, gap 4 |
| Opciones | **"Pantalla"** y **"Teléfono"**; alto 40, pad 0/16, texto `.text-body-sm-strong` |
| Seleccionada | fondo `--accent`, texto `--on-accent` |
| No seleccionada | transparente, texto `--ink-muted`; hover `--surface-subtle` |
| Accesibilidad | contenedor `role="radiogroup"` con `aria-label="Vista de la aplicación"`; cada opción `role="radio"` con `aria-checked`; foco visible `--focus`; flechas ← → cambian la opción |
| Íconos | opcionales (monitor y celular de trazo, 20 px); el set de íconos aún no está elegido, así que puede ir solo texto |

Reglas: no se dibuja dentro del marco del teléfono ni dentro del layout de Pantalla (flota sobre ambos); no se imprime.

---

## 4. Marco de teléfono (`PhoneFrame`) y cambio sin perder estado

Se usa **el mismo árbol de elementos en los dos modos** y se cambia solo un atributo `data-mode`. Si en un modo se envolviera la app con `<PhoneFrame>` y en el otro no, React la reconstruiría y se perdería lo escrito en los formularios.

```tsx
// src/view-mode/ViewModeProvider.tsx
export type ViewMode = 'pantalla' | 'telefono';
const KEY = 'se:viewMode';
const MIN_WIDTH = 1024;

function initialMode(): ViewMode {
  const q = new URLSearchParams(window.location.search).get('vista');
  if (q === 'telefono' || q === 'pantalla') return q;
  const saved = localStorage.getItem(KEY);
  if (saved === 'telefono' || saved === 'pantalla') return saved;
  return 'pantalla';
}
// Exponer por contexto: { mode, setMode, canToggle }
//  canToggle = window.innerWidth >= MIN_WIDTH && ruta actual !== '/admin'
//  Guardar en localStorage al cambiar.
```

```tsx
// src/view-mode/DeviceStage.tsx — envuelve las rutas de la app
export function DeviceStage({ children }: { children: React.ReactNode }) {
  const { mode, canToggle } = useViewMode();
  const framed = canToggle && mode === 'telefono';
  useFitScale(framed);            // ver abajo: escribe --phone-scale en :root
  const tone = useScreenTone();   // 'dark' solo en /check-in
  return (
    <div className="stage" data-mode={framed ? 'telefono' : 'pantalla'}>
      <div className="phone-slot">
        <div className="phone">
          <div className="phone-screen">
            {framed && <StatusBar tone={tone} />}
            <div className="app-scroll">
              <div className="app-shell">{children}</div>
            </div>
          </div>
        </div>
      </div>
      {canToggle && <ViewModeToggle />}
    </div>
  );
}
```

```ts
// Escala el marco para que entre en laptops de poca altura (nunca agranda).
function useFitScale(active: boolean) {
  useEffect(() => {
    const set = () => {
      const s = active ? Math.min(1, (window.innerHeight - 48) / (844 + 20)) : 1;
      document.documentElement.style.setProperty('--phone-scale', String(s));
    };
    set();
    window.addEventListener('resize', set);
    return () => window.removeEventListener('resize', set);
  }, [active]);
}
```

```css
/* Tokens de layout (agregar a tokens.css) */
:root {
  --phone-w: 390px;
  --phone-h: 844px;
  --phone-bezel: 10px;
  --phone-scale: 1;
  --container-max: 1120px;
  --topbar-h: 64px;
}

/* ---- modo Pantalla: el marco no existe visualmente ---- */
.stage[data-mode='pantalla'] .phone-slot,
.stage[data-mode='pantalla'] .phone,
.stage[data-mode='pantalla'] .phone-screen,
.stage[data-mode='pantalla'] .app-scroll { display: contents; }
.app-shell { container: app / inline-size; min-height: 100dvh; background: var(--surface); }

/* ---- modo Teléfono ---- */
.stage[data-mode='telefono'] { min-height: 100dvh; display: grid; place-items: center; background: var(--surface-subtle); padding: var(--space-24); box-sizing: border-box; }
/* el slot ocupa el tamaño ya escalado, para que no aparezca scroll de más */
.stage[data-mode='telefono'] .phone-slot {
  width:  calc((var(--phone-w) + 2 * var(--phone-bezel)) * var(--phone-scale));
  height: calc((var(--phone-h) + 2 * var(--phone-bezel)) * var(--phone-scale));
}
.stage[data-mode='telefono'] .phone {
  width:  calc(var(--phone-w) + 2 * var(--phone-bezel));
  height: calc(var(--phone-h) + 2 * var(--phone-bezel));
  padding: var(--phone-bezel); box-sizing: border-box;
  background: var(--surface-dark); border-radius: 46px;
  transform: scale(var(--phone-scale)); transform-origin: top left;
}
.stage[data-mode='telefono'] .phone-screen { width: var(--phone-w); height: var(--phone-h); border-radius: 36px; overflow: hidden; background: var(--surface); display: flex; flex-direction: column; }
.stage[data-mode='telefono'] .app-scroll { flex: 1; min-height: 0; overflow-y: auto; }
.stage[data-mode='telefono'] .app-shell { min-height: 100%; }
```

Puntos a respetar dentro del marco:

- **`BottomNav` no puede ser `position: fixed`** (dentro de un elemento con `transform` se ancla mal). Va como último hijo de la columna de la pantalla, con `position: sticky; bottom: 0`.
- Usar `100dvh` solo en `.app-shell` de modo Pantalla; dentro del marco la altura es la del `.phone-screen` (844).
- `StatusBar` (9:41, señal, wifi, batería; alto 44) **solo se dibuja dentro del marco**; en Pantalla y en celular real no existe. `tone` es `light` (texto oscuro) o `dark` (texto blanco) y es `dark` únicamente en `/check-in`.
- El marco es un adorno de presentación: es lo único donde se usa `--surface-dark` como bisel; no lleva sombra.

---

## 5. Navegación según el layout

Se renderizan las dos y el container query muestra una u otra (la oculta con `display: none`, así no queda duplicada para lectores de pantalla):

```css
.only-desktop { display: none; }
@container app (min-width: 900px) {
  .only-mobile  { display: none; }
  .only-desktop { display: flex; }
}
```

- Layout móvil → `BottomNav` (`.only-mobile`), como en `SCREENS.md`.
- Layout de Pantalla → `TopBar` (`.only-desktop`), en las pantallas que hoy llevan tabs (Inicio, Reservar, Check-in, Perfil). Login, Registro, Turno confirmado y Viaje registrado no llevan `TopBar` con tabs.

### `TopBar`

| Elemento | Especificación |
|---|---|
| Barra | `position: sticky; top: 0`, alto `--topbar-h` (64), fondo `--surface`, borde inferior 1 px `--line`, pad 0/32 |
| Izquierda | logo `smartelevate-logo-horizontal.svg` a ~32 px de alto, con `alt="SmartElevate"` (ver `DESIGN-SYSTEM.md` §9) |
| Centro/izquierda | tabs **Inicio · Reservar · Check-in · Perfil**, gap 24, `.text-body-sm-strong`; activa en `--accent` con subrayado de 2 px; inactiva `--ink-muted`; hover `--ink`; área táctil ≥ 44 px de alto |
| Derecha | avatar de 36 px + "Juana Martínez" en `.text-body-sm-strong` |

Contenido de cada página en Pantalla: `.page { max-width: var(--container-max); margin: 0 auto; padding: var(--space-32); }`.

---

## 6. Cómo se reacomoda cada pantalla en modo Pantalla

Los textos, datos y componentes son los mismos que en `SCREENS.md`; cambia solo la disposición. Los botones dejan de ir a ancho completo (`block={false}`) salvo donde se indica.

| Pantalla | Layout de Pantalla (contenedor ≥ 900) |
|---|---|
| **01 Login** | Fondo `--surface-subtle`, altura completa. Tarjeta centrada de **440 px** (`--surface`, borde `--line`, `--radius-2xl`, pad 32) con el mismo contenido que en móvil; logo a 120 px. Botón "Ingresar" a ancho completo de la tarjeta. |
| **02 Registro** | Igual que Login con tarjeta de **480 px**. El botón de volver queda dentro de la tarjeta, arriba. Recuperar contraseña y Contraseña nueva (02b, 02c) usan la misma tarjeta. |
| **03 Inicio** | `TopBar` + `.page`. Título de saludo en `.text-title-lg`. Grilla `7fr 5fr`, gap 32. Columna izquierda (gap 16): saludo, "Próxima clase" (pospuesta, [#86](https://github.com/Benji-9/SmartElevate/issues/86)), "Tu turno". Columna derecha: "Congestión ahora" dentro de una tarjeta (pad 20, `--radius-lg`, borde `--line`). En "Tu turno", el botón "Hacer check-in" con `block={false}`. |
| **04 Reservar turno** | `TopBar` + `.page`. Encabezado "Reservar turno" en `.text-title-lg` (sin botón de volver). Grilla `1fr 1fr`, gap 32. Izquierda: Edificio, Piso de destino y el aviso de pisos bajos. Derecha: Franja horaria y debajo una tarjeta resumen (pad 16, `--radius-lg`, fondo `--surface-subtle`) con "Lima 3 · Piso 7 · 7:25 – 7:30" y el botón "Confirmar turno · 7:25" (`block={false}`, alineado a la derecha). La columna derecha es `position: sticky; top: calc(var(--topbar-h) + var(--space-32))`. |
| **05 Turno confirmado** | Sin `TopBar`. Columna centrada de **480 px** con el mismo contenido. Los dos botones van en fila: "Cancelar turno" (secondary) a la izquierda y "Ir a check-in" (primary) a la derecha. |
| **06 Check-in (QR)** | `TopBar` + `.page`. Columna centrada de **480 px**: primero un panel oscuro (`--surface-dark`, `--radius-2xl`, pad 32) con el visor de 250 × 250 y sus dos textos; debajo, una tarjeta clara (borde `--line`, pad 16) con el turno y el botón secondary "Ingresar código manualmente". Ver demo en la sección 8. |
| **07 Viaje registrado** | Igual que la 05: columna de **480 px** sin `TopBar`; "Volver al inicio" a ancho completo. |
| **08 Perfil** | `TopBar` + `.page`. Grilla `5fr 7fr`, gap 32. Izquierda: cabecera del perfil (dentro de una tarjeta) y la lista de ajustes. Derecha: tarjeta "Acceso prioritario". |
| **09 Admin** | Sin cambios respecto de `SCREENS.md` (ver sección 7). |

Estados de puntero (solo con `@media (hover: hover)`): botón primary `opacity: .92`; secondary con fondo `--accent-tint`; filas seleccionables (franjas, pisos, edificio) con fondo `--surface-subtle`; siempre `cursor: pointer` en lo clickeable.

---

## 7. Panel de administración

- Es **solo de escritorio**: no muestra el selector de vista ni usa el marco de teléfono.
- Si el contenedor mide menos de 900 px (celular o ventana angosta), en lugar del panel se muestra un aviso centrado: título `.text-heading` "El panel de administración es para computadora" y texto `--ink-muted` "Abrilo desde una pantalla más grande para ver el detalle de congestión."
- Si alguien cambia a modo Teléfono estando en una pantalla de usuario y navega a `/admin`, se fuerza modo Pantalla mientras esté en esa ruta y se restaura al salir.

---

## 8. Ayudas para la demo en laptop

Pensadas para presentar el MVP; se activan con `?demo=1` en la URL y no aparecen para el usuario final.

- **"Simular escaneo del QR"** en `/check-in`: botón secondary debajo del visor que lleva directo a `/check-in/ok`. En una laptop no hay un QR real que escanear ni una cámara cómoda de usar.
- Los parámetros de URL se combinan: `/?vista=telefono&demo=1` abre la app ya en modo Teléfono con las ayudas de demo, útil para dejar el link listo antes de presentar.

---

## 9. Criterios de aceptación

- [ ] En una ventana de 1440 px aparece el selector y, por defecto, el layout de Pantalla.
- [ ] Al elegir "Teléfono", la app se ve en un marco de 390 × 844 con el layout móvil, centrada; al volver a "Pantalla", ocupa todo el ancho.
- [ ] Cambiar de modo no reinicia la ruta ni borra lo escrito en un formulario.
- [ ] La elección se conserva al recargar y `?vista=` la fuerza.
- [ ] En una laptop de 768 px de alto el marco se achica y no genera scroll de página.
- [ ] En una ventana < 1024 px no hay selector ni marco; en un celular real la app es a pantalla completa.
- [ ] Dentro del marco, `BottomNav` queda pegada abajo y la pantalla scrollea por dentro.
- [ ] `TopBar` y `BottomNav` nunca se ven a la vez.
- [ ] `/admin` no muestra el selector y avisa si el ancho es menor a 900 px.
- [ ] Todas las pantallas cumplen el checklist de accesibilidad de `SCREENS.md` en ambos modos, incluido el selector de vista.
