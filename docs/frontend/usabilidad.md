# Prueba de usabilidad con usuarios reales

Plan y guion para validar con personas reales, en su teléfono y en el campus, las mejoras de la épica [#147](https://github.com/Benji-9/SmartElevate/issues/147). La revisión de #147 fue heurística (la hicimos nosotros); esta prueba la contrasta con uso real. Issue: [#163](https://github.com/Benji-9/SmartElevate/issues/163).

**Cuándo:** con los sub-issues de prioridad alta de #147 cerrados y la app desplegada en un entorno de prueba.

## 1. Objetivo

Saber si una persona que nunca usó SmartElevate puede, sin ayuda:

1. Registrarse.
2. Reservar un turno en una salida del ascensor.
3. Cancelarlo.
4. Hacer check-in con el QR del ascensor.
5. Encontrar su viaje en *Mis viajes*.

Y dónde se traba, duda o se equivoca.

## 2. Participantes

**5 personas**, que no sean del equipo ni hayan visto la app:

| Id | Perfil |
| --- | --- |
| P1–P3 | Estudiantes |
| P4 | Persona con movilidad reducida (estudiante o personal) |
| P5 | Docente |

- Los datos van **anonimizados**: en este archivo solo el id (P1…P5), el perfil y el dispositivo. Nada de nombres, emails, legajos ni datos de salud.
- Antes de empezar se pide el consentimiento oral: qué se hace, que se prueba la app y no a la persona, que puede cortar cuando quiera y que no se graba la cara ni la voz (se toman notas; si se graba la pantalla, se avisa y se borra al pasar las notas en limpio).
- P4 y P5 usan una **cuenta de prueba con la prioridad ya aprobada**: la prueba no incluye subir un certificado real (es un dato de salud).

## 3. Dispositivos y entorno

- **El teléfono de cada participante**, con su navegador de siempre. Que haya al menos un Android y un iPhone en la ronda.
- **Una pasada en modo Pantalla** (laptop), con una de las personas, repitiendo la tarea 2.
- **En el campus**, frente a un núcleo real (p. ej. Lima 1), en un horario de operación (7:00 a 22:30) y fuera del pico, para no molestar.
- Para la tarea de check-in: el QR del ascensor si ya está instalado; si no, un QR de prueba impreso, o el código corto (`/check-in/codigo`). Anotar cuál se usó.
- Las salidas se reservan **de 30 a 2 min antes** ([ventana de reserva](../reglas/turnos.md#2-ventana-de-reserva)): la persona que modera verifica antes de la tarea 2 que haya salidas abiertas.

## 4. Roles del equipo

- **Moderador/a**: lee el guion, da las tareas y no ayuda. Si la persona pregunta, devuelve la pregunta ("¿Qué harías?"). Si queda trabada más de 2 min, ofrece seguir y la tarea cuenta como no completada.
- **Notas**: cronometra y anota en la [planilla](#7-planilla-por-participante).

## 5. Guion

### Introducción (2 min)

> Gracias por venir. Estamos probando una app para reservar lugar en los ascensores de la facultad. Vamos a probar la app, no a vos: si algo no sale, es un problema de la app y nos sirve un montón. Te voy a pedir unas tareas cortas; contanos en voz alta lo que pensás mientras las hacés. No te puedo ayudar durante la tarea, pero al final charlamos. ¿Arrancamos?

### Tareas

Leerlas tal cual, sin nombrar botones ni pantallas. Después de cada una, preguntar: *"Del 1 al 7, ¿qué tan fácil o difícil te resultó?"* (1 = muy difícil, 7 = muy fácil).

| # | Consigna para leer | Se completa cuando… | Prestar atención a… |
| --- | --- | --- | --- |
| 1 | "Creá tu cuenta en la app." | Llega al inicio con la sesión iniciada. | Email institucional, requisitos de la contraseña, tipo de usuario. |
| 2 | "Vas al piso 7 por el núcleo Lima 1. Reservá un lugar en la próxima salida que te sirva." | Ve "¡Turno confirmado!". | Si entiende qué es una salida, los pisos deshabilitados y su motivo, si se da cuenta cuando una salida está completa. |
| 3 | "Desde el inicio, decinos cuánto falta para que salga tu ascensor." | Lo dice en voz alta. | Si encuentra la cuenta regresiva de la tarjeta "Tu turno". |
| 4 | "Cambiaste de idea: cancelá ese turno." | Vuelve al inicio sin turno activo. | Si entiende el diálogo de cancelar y si cancelar a último momento cuenta como falta. |
| 5 | "Reservá de nuevo y, cuando llegue el ascensor, registrá que subiste." | Ve el resultado del check-in. | Permiso de cámara, si encuentra el código manual, si entiende el resultado. |
| 6 | "Buscá el viaje que acabás de hacer." | Encuentra el viaje en *Mis viajes*. | Si encuentra la sección y entiende el resultado de cada viaje. |

**Modo Pantalla:** con una de las personas, repetir la tarea 2 en la laptop.

### Cierre (3 min)

- "¿Qué fue lo más confuso?"
- "¿Qué cambiarías primero?"
- "¿La usarías para ir a clase? ¿Por qué?"
- Solo P4 y P5: "¿Te quedó claro que tenés prioridad y qué cambia por eso?"

## 6. Qué medir

| Medida | Cómo |
| --- | --- |
| Tarea completada | Sí / con ayuda / no. "Con ayuda" cuenta como no completada. |
| Tiempo | Desde que termina de leer la consigna hasta el criterio de "se completa cuando…". |
| Errores | Toques en algo equivocado, vueltas atrás, mensajes de error que vio. |
| Facilidad percibida | La respuesta del 1 al 7 después de cada tarea. |
| Comentarios | Frases textuales, entre comillas. |

## 7. Planilla por participante

Copiar una por persona.

```md
### P? · <perfil> · <Android/iOS, navegador> · <fecha>

| Tarea | Completada | Tiempo | Errores | Facilidad (1–7) | Notas y frases textuales |
| --- | --- | --- | --- | --- | --- |
| 1 Registro | | | | | |
| 2 Reservar | | | | | |
| 3 Cuánto falta | | | | | |
| 4 Cancelar | | | | | |
| 5 Check-in | | | | | |
| 6 Mis viajes | | | | | |

Cierre:
```

## 8. Análisis y salida

1. Juntar las planillas y listar cada problema observado, con cuántas personas lo tuvieron.
2. Priorizar cada hallazgo:
   - **Alta**: impidió completar una tarea o llevó a un error con consecuencias (p. ej. una falta sin querer).
   - **Media**: la completó, pero con dudas, errores o mucho tiempo.
   - **Baja**: comentario o detalle de pulido.
3. Cada hallazgo se convierte en un issue enlazado a #147 (con el principio afectado y la tarea donde apareció) o se descarta con su motivo en la tabla de resultados.

## 9. Resultados

*Pendiente: la primera ronda la hace el equipo con personas reales.* Al terminarla, completar:

### Ronda 1 · <fecha>

| Participante | Perfil | Dispositivo |
| --- | --- | --- |
| P1 | | |

| Tarea | Completaron (de 5) | Tiempo mediano | Facilidad mediana |
| --- | --- | --- | --- |
| 1 Registro | | | |

| Hallazgo | Personas | Prioridad | Issue o motivo del descarte |
| --- | --- | --- | --- |
| | | | |
