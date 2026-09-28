# 0002. Monorepo con backend y frontend

- **Estado:** Aceptado
- **Fecha:** 2026-09-28

## Contexto

El MVP tiene una API (Spring Boot) y una SPA (React). El equipo es chico y los cambios suelen tocar ambos lados a la vez (un endpoint nuevo + la pantalla que lo consume).

## Opciones consideradas

1. **Dos repos** — CI más simple por repo, pero PRs duplicados, versiones desincronizadas y doble configuración (templates, dependabot, protección de ramas).
2. **Monorepo** con `backend/` y `frontend/` — un PR por feature, una sola estrategia de ramas y docs centralizadas.

## Decisión

Monorepo con `backend/` y `frontend/` como proyectos independientes (cada uno con su propio build), sin herramienta de monorepo (Nx, Turborepo): no hace falta para dos proyectos de stacks distintos.

## Consecuencias

- Un workflow de CI por proyecto. Corren siempre (sin filtros `paths`) para poder ser required checks; el costo extra de minutos es bajo.
- Dependabot se configura por directorio.
- El backend está organizado **por feature** (`user`, `elevator`, `turn`, `priority`) con capas adentro, para que cada integrante pueda trabajar un módulo con pocos conflictos.
