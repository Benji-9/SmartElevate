# Cómo contribuir

Guía para sumar cambios a SmartElevate. Para levantar el proyecto en tu máquina, empezá por el [README](README.md); el contexto para agentes de código está en [`AGENTS.md`](AGENTS.md).

## Flujo en una mirada

1. **Issue primero**: todo el trabajo se trackea en [Issues](https://github.com/Benji-9/SmartElevate/issues).
2. **Leer antes de escribir**: [reglas de negocio](docs/reglas/README.md), [glosario](docs/glosario.md), [specs de UI](docs/frontend/README.md) y [ADRs](docs/adr/README.md) que apliquen.
3. **Rama** desde `develop` con el nombre `<prefijo>/<issue>-<kebab-case>`.
4. **Commits** chicos y atómicos (Conventional Commits).
5. **Verificar** con los comandos del lado que tocaste ([backend](backend/README.md#comandos), [frontend](frontend/README.md#comandos)).
6. **PR** contra `develop` con `Closes #N`, solo cuando la tarea está terminada.
7. **Automerge** si el cambio no es crítico.

## Ramas

```
main        ← producción (deploy automático a Vercel prod)
  ↑ PR (merge commit)        ↑ PR
develop     ← integración    hotfix/*  ← urgencias en producción (sale de main)
  ↑ PR (squash)
feature/* · fix/* · chore/* · docs/* · refactor/* · test/* · ci/*   (salen de develop)
```

| Prefijo | Para qué | Sale de → vuelve a | Label automático |
| --- | --- | --- | --- |
| `feature/` | Funcionalidad nueva | `develop` → `develop` | `type: feature` |
| `fix/` | Bug encontrado en `develop` | `develop` → `develop` | `type: bug` |
| `chore/` | Dependencias, config, tooling | `develop` → `develop` | `type: chore` |
| `docs/` | Solo documentación | `develop` → `develop` | `type: docs` |
| `refactor/` | Cambio interno sin impacto funcional | `develop` → `develop` | `type: refactor` |
| `test/` | Tests nuevos o ajustados | `develop` → `develop` | `type: test` |
| `ci/` | Workflows, deploy | `develop` → `develop` | `type: ci-cd` |
| `hotfix/` | Bug urgente en producción | `main` → `main` y después `develop` | `type: bug`, `priority: high` |

**Nombre:** `<prefijo>/<numero-de-issue>-<descripcion-en-kebab-case>`, en minúsculas. Ej.: `feature/12-reservar-turno`, `fix/27-cupo-negativo`.

```bash
git switch develop && git pull
git switch -c feature/12-reservar-turno
# ... commits ...
git push -u origin feature/12-reservar-turno   # y abrir PR contra develop
```

**Flujos:**

- **Trabajo diario:** rama desde `develop` → PR a `develop` → merge con **squash** (un commit por PR, con el título del PR).
- **Release:** PR `develop → main` → merge con **merge commit** (no squash, para que ambas ramas compartan historia). Dispara el deploy de producción.
- **Hotfix:** rama `hotfix/*` desde `main` → PR a `main` → después, PR `main → develop` para no perder el arreglo.

**Reglas (automáticas):**

- `main` y `develop` están protegidas: nadie pushea directo, todo entra por PR con los checks en verde (`backend-verify`, `frontend-verify`, `branch-name`), rama al día y conversaciones resueltas. La review **no es obligatoria**: GitHub se la pide automáticamente a los code owners ([`.github/CODEOWNERS`](.github/CODEOWNERS)).
- El workflow [`branch-policy.yml`](.github/workflows/branch-policy.yml) falla el PR si la rama no respeta la convención o si a `main` llega algo que no sea `develop` o `hotfix/*`, y etiqueta el PR según el prefijo.

## Commits

Usamos [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/):

```
<tipo>(<scope opcional>): <descripción en imperativo>
```

Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `style`, `perf`, `build`, `ci`, `chore`.
Scopes sugeridos: `backend`, `frontend`, `turn`, `elevator`, `user`, `priority`, `vercel`, `adr`.

```
feat(turn): permitir reservar un turno en una franja con cupo
fix(frontend): mostrar error cuando la franja está llena
ci: cachear dependencias de Maven
```

- **Un commit, un cambio lógico.** Si el mensaje necesita un "y", son dos commits.
- **Título** en imperativo, hasta ~72 caracteres, sin punto final.
- **Cuerpo** (opcional, separado por una línea en blanco) para explicar el **por qué**, no el qué: el diff ya muestra el qué.
- **Referenciá el issue** al final del cuerpo (`Refs #12`). `Closes #12` va en la descripción del PR.
- Cambios que rompen compatibilidad: `feat(api)!: ...` y una línea `BREAKING CHANGE:` en el cuerpo.
- Código (identificadores) y commits en inglés; documentación y UI en español rioplatense.

## Issues y labels

Usá el formulario que corresponda: Feature, Bug, Tarea técnica, Documentación, Cambio de contrato API o Bloqueante.

- Título que dice el objetivo.
- Historia de usuario (`Como <rol>, quiero <qué>, para <por qué>`) cuando es visible para una persona.
- **Criterios de aceptación verificables**: si no se puede comprobar, no es un criterio.
- Qué queda fuera de alcance.

| Grupo | Labels |
| --- | --- |
| Tipo | `type: feature`, `type: bug`, `type: chore`, `type: docs`, `type: ci-cd`, `type: decision` |
| Área | `area: backend`, `area: frontend`, `area: infra`, `area: docs` |
| Prioridad | `priority: high`, `priority: medium`, `priority: low` |
| Estado | `status: blocked`, `setup: manual` (paso fuera del código, en GitHub/Vercel) |

Cada issue lleva al menos un `type:` y un `area:`. Los bloqueantes se registran además en [`docs/bloqueantes.md`](docs/bloqueantes.md).

## Pull requests

El [template](.github/PULL_REQUEST_TEMPLATE.md) se completa solo. Contenido esperado:

- Contexto y `Closes #N`.
- Qué cambia y cómo.
- Archivos y áreas tocadas.
- Impacto (contrato OpenAPI, migraciones, reglas de negocio, variables de entorno).
- Cómo probarlo.
- Riesgos y cómo revertirlo.

Un PR hace una sola cosa y se revisa en una sentada. Con **squash**, el título del PR es el commit que queda en `develop`: escribilo como tal. No abras PRs de trabajo a medias.

### Automerge y cambios críticos

Si el cambio no es crítico, activá `gh pr merge --auto --squash`: mergea solo cuando pasan los CI requeridos.

Es **crítico** (sin automerge, esperá revisión humana) si toca:

- cupo, prioridades o las reglas de [`docs/reglas/`](docs/reglas/README.md);
- auth, seguridad o JWT;
- la marca de movilidad reducida ([ADR 0024](docs/adr/0024-movilidad-reducida-la-marca-viene-de-ofalu.md));
- migraciones Flyway;
- workflows de `.github/` o el deploy;
- un ADR.

Ante la duda, tratalo como crítico.

## Contrato de la API

Si cambiás endpoints o DTOs, regenerá `docs/openapi.json` y los tipos del frontend y commiteá los dos archivos en el mismo PR. Los pasos están en el [README del backend](backend/README.md#contrato-openapi).

## Decisiones y bloqueantes

- Decisión de arquitectura o de proceso difícil de revertir: nuevo ADR ([cómo](docs/adr/README.md#cómo-agregar-uno)).
- Algo que bloquea: [`docs/bloqueantes.md`](docs/bloqueantes.md).
- Al fijar versiones de dependencias o actions, verificá la última versión real (Maven Central, npm, GitHub releases); no uses versiones de memoria.

## Seguridad

Para vulnerabilidades no abras un issue público: seguí [`SECURITY.md`](SECURITY.md). No commitees secretos, credenciales ni datos personales.
