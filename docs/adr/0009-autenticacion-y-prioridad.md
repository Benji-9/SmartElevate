# 0009. Autenticación con email institucional y prioridad validada en el servidor

- **Estado:** Propuesto
- **Fecha:** 2026-09-28
- **Issues:** [#6](https://github.com/Benji-9/SmartElevate/issues/6), [#32](https://github.com/Benji-9/SmartElevate/issues/32), [#24](https://github.com/Benji-9/SmartElevate/issues/24), [#25](https://github.com/Benji-9/SmartElevate/issues/25), [#26](https://github.com/Benji-9/SmartElevate/issues/26)

## Contexto

Hay que identificar a los alumnos y marcar a los usuarios prioritarios (movilidad reducida, docentes) sin acceso a los sistemas de UADE. El certificado que respalda la movilidad reducida es un **dato de salud** (Ley 25.326).

## Opciones consideradas

1. **SSO de UADE**: ideal, pero no hay acceso.
2. **Email @uade.edu.ar verificado por link + legajo autodeclarado**: la verificación real es la del email.
3. **Alta manual por un admin**: no escala.

Para la prioridad en la sesión:

- **a. Claim en el JWT**: la prioridad puede vencer o revocarse mientras el token sigue vigente.
- **b. Consultarla en el servidor en cada reserva.**

## Decisión

- Opción **2**, con email y legajo únicos.
- **Rol y prioridad nunca vienen del registro**: los asigna un ADMIN.
- El registro tiene un **tipo de usuario declarativo** (Estudiante / Docente) que no otorga nada. Los docentes declarados quedan en una cola del panel de administración, y el ADMIN los aprueba o rechaza.
- El **primer ADMIN** se crea promoviendo a un usuario registrado con SQL en la consola de Neon. No hay endpoint ni seed, así que ningún dato del admin queda en el repo.
- Movilidad reducida por certificado: **PENDIENTE → APROBADO/RECHAZADO**, siempre con **vencimiento**; al vencer, el usuario vuelve a ser común.
- JWT con **access token corto (~15 min) y refresh**. La prioridad se evalúa **en el servidor** (opción **b**).
- Archivos: tipo validado **por contenido**, con tope de tamaño.
- Datos de salud: **consentimiento explícito**, **bucket privado** accesible solo por `REVIEWER` con **URLs firmadas cortas**, y **borrado del archivo al resolver**, conservando solo el estado y el vencimiento.

Detalle: [usuarios y prioridad](../reglas/usuarios-y-prioridad.md).

## Consecuencias

- Hace falta Spring Security, un proveedor de email ([#25](https://github.com/Benji-9/SmartElevate/issues/25)) y storage privado ([#26](https://github.com/Benji-9/SmartElevate/issues/26)).
- Una consulta extra por reserva para obtener la prioridad vigente (barata).
- El tratamiento de datos de salud debe validarse ([#24](https://github.com/Benji-9/SmartElevate/issues/24)). Esto **no es asesoramiento legal**.
