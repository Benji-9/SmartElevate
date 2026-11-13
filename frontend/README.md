# Frontend

SPA de SmartElevate: React 19, Vite 8, TypeScript, React Router, Vitest + React Testing Library, ESLint + Prettier. Se despliega en Vercel desde GitHub Actions ([ADR 0003](../docs/adr/0003-deploy-frontend-vercel-via-github-actions.md)).

## Requisitos

**Node.js 24 LTS** (ver [`.nvmrc`](.nvmrc); con nvm: `nvm use`).

## Comandos

Desde `frontend/`:

```bash
npm ci                    # instalar dependencias (o npm install)
cp .env.example .env.local   # opcional: el default ya es /api
npm run dev               # http://localhost:5173
npm run lint
npm test -- --run         # una pasada (sin --run queda en watch)
npm run build             # tsc -b + vite build
npm run format            # prettier --write (chequear sin escribir: npm run format:check)
npm run gen:api           # regenera src/types/openapi.ts desde ../docs/openapi.json
```

Lo mismo que corre el CI: `npm ci && npm run gen:api && npm run lint && npm test -- --run && npm run build` (el CI además falla si `gen:api` cambia `src/types/openapi.ts`).

En dev, Vite hace proxy de `/api` a `http://localhost:8080` (el [backend](../backend/README.md#comandos)).

## Variables de entorno

| Variable | Default | Descripción |
| --- | --- | --- |
| `VITE_API_URL` | `/api` | Base del cliente HTTP (`services/api.ts`). En Vercel tiene que ser `/api` para que la cookie del refresh sea del mismo origen ([ADR 0019](../docs/adr/0019-refresh-token-en-cookie-httponly.md)) |
| `VITE_USE_MOCKS` | `true` en `npm run dev`, `false` en tests | `true` usa datos simulados en lugar del backend (`services/mocks.ts`); con `false`, `npm run dev` le pega al backend real. Nunca aplica al build de producción |

Las de Vercel y GitHub (secrets, `BACKEND_URL`): [Configurar Vercel y GitHub](../docs/guias/configurar-vercel-y-github.md).

## Estructura

```
src/
├── pages/        una pantalla por ruta
├── components/   UI reutilizable
├── features/     lógica y componentes por dominio (admin, auth, check-in, congestion, kiosk, priority, trips, turn, view-mode)
├── services/     api.ts: único cliente HTTP (y mocks.ts)
├── hooks/
└── types/        openapi.ts (generado) y api.ts (alias)
vercel.json       rewrite de /api al backend y headers de seguridad
scripts/          generar-elevy.py (poses SVG de la mascota; uso en la cabecera del script)
```

## Convenciones

- Los tipos de la API salen de `src/types/openapi.ts` (**generado, no editar**) y se exponen con alias en `src/types/api.ts`. No definir DTOs a mano.
- Toda llamada al backend pasa por `services/api.ts`. No usar `fetch` directo en componentes.
- Rutas en `App.tsx` (importar de `react-router`). `App` no incluye el router: `main.tsx` usa `BrowserRouter`, los tests `MemoryRouter`.
- Estilos solo con `var(--…)` (nunca hex sueltos); layout con `@container`, nunca `@media` de ancho.
- **Specs de UI** en [`docs/frontend/`](../docs/frontend/README.md): antes de tocar una pantalla o componente leé `DESIGN-SYSTEM.md`, `SCREENS.md` y `VIEW-MODES.md`. No llamar al MCP de Figma.
- Si una spec contradice a [`docs/reglas/`](../docs/reglas/README.md) o al contrato OpenAPI, mandan estos últimos: frená y preguntá.
- Prettier: comillas simples, `;`, `trailingComma: all`, ancho 100.

## Tests

Vitest + Testing Library, entorno jsdom (`src/setupTests.ts`). Mockeá `fetch` con `vi.stubGlobal` y buscá por rol o texto accesible, no por clases. Los tests viven al lado del código (`Componente.test.tsx`).

## Contrato de la API

Si el backend cambió endpoints o DTOs, corré `npm run gen:api` y commiteá `src/types/openapi.ts`. El flujo completo está en el [README del backend](../backend/README.md#contrato-openapi).
