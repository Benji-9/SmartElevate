# ADR-0003: Deploy del frontend a Vercel vía GitHub Actions

## Metadata

- **Status:** Accepted
- **Date:** 2026-09-28
- **Deciders:** Equipo SmartElevate
- **Owner:** No figura en el ADR original
- **Related Issues:** No aplica
- **Related ADRs:** [ADR-0022](0022-csp-y-headers-de-seguridad-en-vercel.md) (CSP y headers en Vercel)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0003-deploy-frontend-vercel-via-github-actions.md`)

---

## 1. Context and Problem Statement

### Context

Queremos previews por PR y producción desde `main`, pero **solo si el CI pasó**. La integración nativa Git ↔ Vercel deploya cada push sin esperar a nuestro CI y no respeta la estrategia `main ← develop ← feature/*`.

### Problem

¿Cómo deployar el frontend a Vercel únicamente cuando el CI está en verde y respetando nuestra estrategia de ramas?

---

## 2. Decision Drivers

- Deploy condicionado al éxito del CI.
- Previews por PR y producción desde `main`.
- Respetar la estrategia de ramas `main ← develop ← feature/*`.
- CD desacoplado del CI.
- Seguridad: no ejecutar código ajeno con acceso a secrets.

---

## 3. Considered Options

### Option A: Integración Git de Vercel

**Pros:**
- Cero configuración.

**Cons:**
- Deploya aunque el CI falle.
- No respeta la estrategia de ramas.

### Option B: Job de deploy con `needs:` dentro de `ci-frontend.yml`

**Pros:**
- Simple.

**Cons:**
- Mezcla CI y CD.
- El deploy de un PR queda como un check más del PR.

### Option C: Workflow separado con `workflow_run` sobre "CI Frontend"

**Pros:**
- CD desacoplado del CI.
- Corre solo con conclusión `success`.

**Cons:**
- Limitaciones de `workflow_run` (ver *Consequences*).

---

## 4. Decision Outcome

**Chosen Option:** Option C, usando la Vercel CLI (`vercel pull` → `vercel build` → `vercel deploy --prebuilt`) y `"git": { "deploymentEnabled": false }` en `vercel.json` para que el workflow sea el único camino de deploy.

### Rationale

Es la única opción que desacopla CD de CI y deploya solo con CI en verde.

- PR → preview + comentario (se actualiza el mismo comentario en cada push).
- Push a `develop` → preview. Push a `main` → producción.
- Se deploya exactamente el `head_sha` que validó el CI.
- PRs desde forks se ignoran: `workflow_run` tiene acceso a secrets y no queremos ejecutar código ajeno con ellos.

### URL del backend

El backend se hostea fuera de Vercel. Vercel no interpola variables de entorno en `vercel.json`, así que el rewrite usa el placeholder literal `${BACKEND_URL}` y el workflow lo reemplaza (con `jq`) por la variable de GitHub `BACKEND_URL` antes de `vercel build`. Si no está definida, se quita el rewrite y se emite un warning.

---

## 5. Consequences

### Positive Consequences

- Solo se deploya código que pasó el CI, exactamente el `head_sha` validado.
- Previews por PR con comentario actualizable.

### Negative Consequences

- `workflow_run` solo se dispara con la versión del workflow que está en `main`: hasta el primer merge a `main` no hay deploys, y los cambios a `deploy-frontend.yml` no se pueden probar desde un PR.
- El deploy de un PR no aparece como check del PR; la URL llega por comentario y en el summary del run.

### Technical Implications

- El Root Directory del proyecto en Vercel debe quedar vacío (la CLI ya corre desde `frontend/`).
- `frontend/vercel.json` lleva el placeholder `${BACKEND_URL}`; no se cambia por una URL real.
- Si se renombra el workflow "CI Frontend", hay que actualizar `deploy-frontend.yml`.

---

## 6. Implementation and Validation

- [x] `deploy-frontend.yml` con `workflow_run` sobre "CI Frontend" (solo `success`).
- [x] `"git": { "deploymentEnabled": false }` en `vercel.json`.
- [x] Reemplazo de `${BACKEND_URL}` con `jq` antes de `vercel build`.
- [x] Root Directory vacío en el proyecto de Vercel.

---

## 7. References

- `.github/workflows/deploy-frontend.yml` y `frontend/vercel.json`.
- [ADR-0022](0022-csp-y-headers-de-seguridad-en-vercel.md).
