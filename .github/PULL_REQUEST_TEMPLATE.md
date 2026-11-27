<!--
Título del PR: Conventional Commits, en imperativo y sin punto final. Con squash, este título
pasa a ser el commit en `develop`: tiene que explicar el cambio solo.
  feat(turn): permitir reservar una salida con cupo
-->

## Resumen

<!-- En una o dos frases: qué cambia. -->

## Contexto

<!-- Por qué hace falta: el problema, la decisión o la regla de negocio detrás. Linkeá el issue
("Closes #123") y, si aplica, el ADR o la regla de docs/reglas/. -->

Closes #

## Historia de usuario

<!-- Si el cambio es visible para una persona usuaria. Borrá la sección si es técnico. -->

Como **<rol>**, quiero **<qué>**, para **<por qué>**.

## Qué cambia y cómo

<!-- El enfoque elegido y las alternativas descartadas, en pocas líneas. No repitas el diff. -->

## Archivos y áreas tocadas

<!-- Agrupá por carpeta o capa y decí qué se hizo en cada una. -->

| Área | Archivos | Cambio |
| ---- | -------- | ------ |
|      |          |        |

## Tipo de cambio

- [ ] `feat` — nueva funcionalidad
- [ ] `fix` — corrección de bug
- [ ] `refactor` — cambio interno sin impacto funcional
- [ ] `perf` — mejora de rendimiento
- [ ] `test` — tests nuevos o ajustados
- [ ] `docs` — solo documentación
- [ ] `ci` / `build` / `chore` — pipeline, dependencias, tooling

## Impacto

<!-- Marcá lo que aplique y aclará en una línea. -->

- [ ] Cambia una regla de negocio (`docs/reglas/`) o requiere un ADR
- [ ] Cambia el contrato de la API (`docs/openapi.json` y `src/types/openapi.ts` regenerados)
- [ ] Agrega una migración Flyway (`V<n>__...sql`) y actualicé el DER (`docs/referencia/der.md`)
- [ ] Agrega o cambia variables de entorno (actualicé `.env.example`) o `app_config`
- [ ] Rompe compatibilidad (describí cómo migrar)
- [ ] Toca auth, cupo, prioridades o workflows: requiere revisión humana (sin automerge)

## Cómo probarlo

<!-- Pasos concretos para que quien revisa lo reproduzca, y qué resultado esperar. -->

1.
2.
3.

## Riesgos y rollback

<!-- Qué puede salir mal y cómo se revierte (revert del PR, flag, migración inversa). -->

## Capturas (si toca UI)

<!-- Antes / después, en escritorio y en teléfono. Borrá la sección si no aplica. -->

## Notas para quien revisa

<!-- Por dónde empezar, qué mirar con más cuidado, deuda que dejaste a propósito. -->

## Checklist

- [ ] El PR hace una sola cosa y es lo bastante chico para revisarse
- [ ] Agregué o actualicé tests y pasan localmente (`./mvnw verify` / `npm test -- --run`)
- [ ] El lint pasa (`npm run lint`) y el código está formateado (`npm run format`)
- [ ] Actualicé la documentación (README, `docs/`, ADR) si hacía falta
- [ ] El título sigue Conventional Commits y los commits son atómicos
- [ ] No hay secretos, credenciales ni datos personales en el diff
