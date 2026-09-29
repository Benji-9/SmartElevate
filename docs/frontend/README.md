# Specs de UI (frontend)

Especificación del frontend extraída del wireframe de Figma, versionada acá para implementar **sin llamar al MCP de Figma** (tiene límite de uso). Español rioplatense.

| Archivo | Qué define | Leelo cuando… |
|---|---|---|
| [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md) | Tokens CSS, tipografía, componentes (`Button`, `Input`, `Chip`, `CongestionRow`, `BottomNav`), voz y contenido | toques estilos o componentes |
| [SCREENS.md](SCREENS.md) | Las 9 pantallas móviles: rutas, medidas, textos, estados y checklist de accesibilidad | implementes o cambies una pantalla |
| [VIEW-MODES.md](VIEW-MODES.md) | Modo Pantalla / Teléfono, container queries, `TopBar`, layout de escritorio, ayudas de demo | toques layout o navegación |

## Orden de prioridad ante contradicciones

1. [`docs/reglas/`](../reglas/README.md) y los ADRs (reglas de negocio).
2. El contrato [`docs/openapi.json`](../openapi.json): los datos de ejemplo de `SCREENS.md` son ficticios y hay que alinearlos con la API.
3. Estas specs (mandan sobre Figma).
4. Figma, solo para una duda visual puntual (un frame por ID, ver `DESIGN-SYSTEM.md` §10).

Si algo es ambiguo, se pregunta en vez de elegir. Ejemplo conocido: las franjas de 5 min del wireframe vs. la salida de ascensor de ~2 min (ADR 0007).

Estas specs se actualizan por PR (`docs/`) cuando cambia una decisión de diseño.
