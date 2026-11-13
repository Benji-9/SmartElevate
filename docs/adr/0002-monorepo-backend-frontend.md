# ADR-0002: Monorepo con backend y frontend

## Metadata

- **Status:** Accepted
- **Date:** 2026-09-28
- **Deciders:** Equipo SmartElevate
- **Owner:** No figura en el ADR original
- **Related Issues:** No aplica
- **Related ADRs:** No aplica
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0002-monorepo-backend-frontend.md`)

---

## 1. Context and Problem Statement

### Context

El MVP tiene una API (Spring Boot) y una SPA (React). El equipo es chico y los cambios suelen tocar ambos lados a la vez (un endpoint nuevo + la pantalla que lo consume).

### Problem

¿Un repo o dos? ¿Cómo organizamos el código para trabajar en paralelo sin fricción?

---

## 2. Decision Drivers

- Cambios que cruzan backend y frontend: conviene un solo PR por feature.
- Equipo chico: una sola estrategia de ramas y docs centralizadas.
- Evitar configuración duplicada (templates, dependabot, protección de ramas).
- No sumar herramientas que dos proyectos de stacks distintos no justifican.

---

## 3. Considered Options

### Option A: Dos repos

**Pros:**
- CI más simple por repo.

**Cons:**
- PRs duplicados.
- Versiones desincronizadas.
- Doble configuración (templates, dependabot, protección de ramas).

### Option B: Monorepo con `backend/` y `frontend/`

**Pros:**
- Un PR por feature.
- Una sola estrategia de ramas.
- Docs centralizadas.

**Cons:**
- Dos pipelines de CI en el mismo repo (ver *Consequences*).

---

## 4. Decision Outcome

**Chosen Option:** Option B, monorepo con `backend/` y `frontend/` como proyectos independientes (cada uno con su propio build), sin herramienta de monorepo (Nx, Turborepo).

### Rationale

Los cambios tocan ambos lados a la vez y el equipo es chico, así que un PR por feature y una sola configuración pesan más que la simplicidad del CI por repo. Una herramienta de monorepo no hace falta para dos proyectos de stacks distintos.

---

## 5. Consequences

### Positive Consequences

- Un PR por feature, una sola estrategia de ramas y docs centralizadas.
- El backend está organizado **por feature** (`user`, `elevator`, `turn`, `priority`) con capas adentro, para que cada integrante pueda trabajar un módulo con pocos conflictos.

### Negative Consequences

- Los workflows de CI corren siempre (sin filtros `paths`) para poder ser required checks; el costo extra de minutos es bajo.

### Technical Implications

- Un workflow de CI por proyecto.
- Dependabot se configura por directorio.

---

## 6. Implementation and Validation

- [x] Estructura `backend/` y `frontend/` con builds independientes.
- [x] Un workflow de CI por proyecto, sin filtros `paths`.
- [x] Dependabot configurado por directorio.
- [x] Backend organizado por feature con capas adentro.

---

## 7. References

- Estructura y convenciones: `CLAUDE.md` (sección Arquitectura).
