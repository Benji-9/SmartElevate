@AGENTS.md

# Claude Code

Todo el contexto del repo (comandos, flujo, reglas de negocio, arquitectura) está en `AGENTS.md`, importado arriba. Acá va solo lo específico de Claude Code. No dupliques contenido de `AGENTS.md`.

## Agentes (`.claude/agents/`, versionados)

- `backend-dev` — implementa features del backend siguiendo esta estructura.
- `frontend-dev` — implementa pantallas/features del frontend.
- `test-writer` — escribe o completa tests (JUnit/MockMvc y Vitest/RTL).
- `code-reviewer` — revisa un diff contra las convenciones del repo (solo lectura).
- `devops` — workflows de GitHub Actions, Vercel, Docker.
- `docs-keeper` — ADRs, `bloqueantes.md` y README.

## Configuración local (`.claude/`)

- `settings.json`, `agents/` y `rules/` se versionan: llevan los permisos compartidos (deny de push a `main`/`develop`, force-push, `.env`), la atribución de commits/PRs desactivada y las reglas por zona.
- Todo lo demás de `.claude/` (`skills/`, `settings.local.json`, `worktrees/`…) y `CLAUDE.local.md` es personal y está en `.gitignore`. Lo que generan agentes y skills (`.superpowers/`, `docs/superpowers/`, `.playwright-mcp/`…) tampoco se versiona. Las skills se instalan por persona; no dependas de una skill concreta en el flujo del repo.
- Preferencias personales: `CLAUDE.local.md` (ignorado). Si un archivo supera ~200 líneas, mover lo específico de una zona a `.claude/rules/` con `paths:`.
