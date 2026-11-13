# Configurar Vercel y GitHub (setup inicial)

Quedó hecho en B-02, B-03 y B-09 ([bloqueantes](../bloqueantes.md)). Se deja como referencia para rearmarlo. Issues con checklist: Vercel [#1](https://github.com/Benji-9/SmartElevate/issues/1) · secrets [#2](https://github.com/Benji-9/SmartElevate/issues/2) · protección de ramas [#3](https://github.com/Benji-9/SmartElevate/issues/3) · primer release [#4](https://github.com/Benji-9/SmartElevate/issues/4).

## 1. Vercel: crear el proyecto y linkearlo

1. Crear una cuenta/equipo en [vercel.com](https://vercel.com) (el plan Hobby alcanza para el MVP).
2. Instalar la CLI y loguearse:
   ```bash
   npm install --global vercel
   vercel login
   ```
3. Desde `frontend/`, linkear (o crear) el proyecto:
   ```bash
   cd frontend
   vercel link
   ```
   Elegir el scope (equipo) y "Link to existing project? No" → nombre `smartelevate`. Se crea `frontend/.vercel/project.json` (está en `.gitignore`, **no se commitea**) con:
   ```json
   { "orgId": "team_xxx", "projectId": "prj_xxx" }
   ```
   Esos son `VERCEL_ORG_ID` y `VERCEL_PROJECT_ID`.
4. En el dashboard del proyecto → **Settings → General → Root Directory**: dejarlo **vacío**. El workflow ya corre la CLI desde `frontend/`; si se pone `frontend` ahí, la CLI busca `frontend/frontend` y falla.
5. **Settings → Git**: no conectar el repo (o, si se conectó, igual queda desactivado por `vercel.json`).

## 2. Vercel: crear el token

**Account Settings → Tokens → Create Token**, con scope al equipo del proyecto. Copiarlo: es `VERCEL_TOKEN`.

## 3. GitHub: cargar secrets y variables

En el repo → **Settings → Secrets and variables → Actions**:

- **Secrets** (pestaña *Secrets*):
  - `VERCEL_TOKEN`
  - `VERCEL_ORG_ID`
  - `VERCEL_PROJECT_ID`
- **Variables** (pestaña *Variables*):
  - `BACKEND_URL` — URL pública del backend, sin `/` final (hoy `https://smartelevate.onrender.com`). Se puede definir distinta por environment (`preview` / `production`) en **Settings → Environments**; los environments los crea el workflow en la primera corrida.

## 4. Vercel: variables de entorno

En el proyecto → **Settings → Environment Variables** (para *Production* y *Preview*):

- `VITE_API_URL` = `/api` (usa el rewrite de `vercel.json`; **hace falta** para que la cookie del refresh sea del mismo origen, ADR 0019). Con la URL absoluta del backend la sesión no se mantiene (cookie cross-site descartada).

`vercel pull` las baja en cada deploy y Vite las embebe en el build.

## 5. GitHub: protección de ramas

**Settings → Branches → Add rule** (o *Rulesets*) para `main` y `develop`:

- Require a pull request before merging (sin aprobaciones obligatorias; las reviews se piden por CODEOWNERS)
- Require status checks to pass: `backend-verify`, `frontend-verify` y `branch-name`
- Require branches to be up to date before merging
- Block force pushes

## 6. Primer merge a `main`

GitHub solo dispara `workflow_run` con la versión del workflow que está en la rama por defecto. **El deploy empieza a funcionar recién cuando `deploy-frontend.yml` llega a `main`.**
