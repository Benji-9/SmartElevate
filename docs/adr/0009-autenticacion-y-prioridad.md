# ADR-0009: Autenticación con email institucional y prioridad validada en el servidor

## Metadata

- **Status:** Accepted (parcialmente reemplazado por [ADR-0024](0024-movilidad-reducida-la-marca-viene-de-ofalu.md) en lo de certificados, revisión y datos de salud)
- **Date:** 2026-09-28
- **Deciders:** equipo SmartElevate
- **Owner:** equipo SmartElevate
- **Related Issues:** [#6](https://github.com/Benji-9/SmartElevate/issues/6), [#32](https://github.com/Benji-9/SmartElevate/issues/32), [#24](https://github.com/Benji-9/SmartElevate/issues/24), [#25](https://github.com/Benji-9/SmartElevate/issues/25), [#26](https://github.com/Benji-9/SmartElevate/issues/26)
- **Related ADRs:** [ADR-0013](0013-email-con-brevo-y-storage-en-r2.md), [ADR-0024](0024-movilidad-reducida-la-marca-viene-de-ofalu.md)
- **Nota:** reescrito al formato MADR el 2026-11-13 (versión anterior: `git show d5a37be:docs/adr/0009-autenticacion-y-prioridad.md`)

> **Nota:** el ADR-0024 reemplazó lo de movilidad reducida por certificado (estados PENDIENTE/APROBADO/RECHAZADO, vencimiento, bucket privado, URLs firmadas, consentimiento y borrado del archivo) y todo lo que implicaba tratar datos de salud. Ese flujo ya no existe y no se documenta acá: ver el ADR-0024. Siguen vigentes la autenticación, el rol y la prioridad asignados por un ADMIN, el JWT y la evaluación de la prioridad en el servidor.

---

## 1. Context and Problem Statement

### Context

Hay que identificar a los alumnos y marcar a los usuarios prioritarios (movilidad reducida, docentes) sin acceso a los sistemas de UADE. En el momento de este ADR, el certificado que respaldaba la movilidad reducida se consideraba un **dato de salud** (Ley 25.326); esa parte la reemplazó el ADR-0024.

### Problem

¿Cómo identificamos a las personas, cómo asignamos rol y prioridad sin confiar en lo que declara el registro, y dónde se evalúa la prioridad en cada sesión?

---

## 2. Decision Drivers

- No hay acceso al SSO ni a los sistemas de UADE.
- Rol y prioridad no pueden venir del registro (se autodeclararían).
- La prioridad puede vencer o revocarse mientras el token sigue vigente.
- Un alta manual por un admin no escala.

---

## 3. Considered Options

Identificación:

### Option A: SSO de UADE

**Pros:**
- Es lo ideal.

**Cons:**
- No hay acceso.

### Option B: Email @uade.edu.ar verificado por link + legajo autodeclarado

**Pros:**
- La verificación real es la del email.

**Cons:**
- El legajo es autodeclarado.

### Option C: Alta manual por un admin

**Cons:**
- No escala.

Prioridad en la sesión:

### Option a: Claim en el JWT

**Cons:**
- La prioridad puede vencer o revocarse mientras el token sigue vigente.

### Option b: Consultarla en el servidor en cada reserva

**Pros:**
- Siempre se usa la prioridad vigente.

---

## 4. Decision Outcome

**Chosen Option:** B (email institucional verificado + legajo) para la identificación, y b (consulta en el servidor en cada reserva) para la prioridad.

### Rationale

- Opción **B**, con email y legajo únicos.
- **Rol y prioridad nunca vienen del registro**: los asigna un ADMIN.
- El registro tiene un **tipo de usuario declarativo** (Estudiante / Docente) que no otorga nada. Los docentes declarados quedan en una cola del panel de administración, y el ADMIN los aprueba o rechaza.
- El **primer ADMIN** se crea promoviendo a un usuario registrado con SQL en la consola de Neon. No hay endpoint ni seed, así que ningún dato del admin queda en el repo.
- JWT con **access token corto (~15 min) y refresh**. La prioridad se evalúa **en el servidor** (opción **b**), nunca desde un claim del JWT.
- Movilidad reducida: ver la nota de arriba. Hoy la marca la pone UADE vía OFALU y la app no maneja certificados ni datos de salud (ADR-0024).

Detalle: [usuarios y prioridad](../reglas/usuarios-y-prioridad.md).

---

## 5. Consequences

### Positive Consequences

- La prioridad que se aplica es siempre la vigente, aunque el token tenga hasta ~15 min de vida.
- Ningún dato del primer admin queda en el repo.

### Negative Consequences

- Una consulta extra por reserva para obtener la prioridad vigente (barata).

### Technical Implications

- Hace falta Spring Security y un proveedor de email ([#25](https://github.com/Benji-9/SmartElevate/issues/25), ver [ADR-0013](0013-email-con-brevo-y-storage-en-r2.md)).
- El storage privado previsto en [#26](https://github.com/Benji-9/SmartElevate/issues/26) y la validación legal de datos de salud ([#24](https://github.com/Benji-9/SmartElevate/issues/24)) quedaron sin objeto por el ADR-0024.

---

## 6. Implementation and Validation

- [x] Registro con email @uade.edu.ar verificado por link, email y legajo únicos.
- [x] Rol y prioridad asignados solo por ADMIN; tipo de usuario declarativo con cola de aprobación de docentes.
- [x] JWT con access token corto y refresh; la prioridad se consulta en el servidor al reservar.
- [ ] Mantener la regla en revisión de código: la prioridad nunca se lee de un claim del JWT ni del registro.

---

## 7. References

- [Usuarios y prioridad](../reglas/usuarios-y-prioridad.md)
- [ADR-0013](0013-email-con-brevo-y-storage-en-r2.md) (email)
- [ADR-0024](0024-movilidad-reducida-la-marca-viene-de-ofalu.md) (movilidad reducida)
- Issues [#6](https://github.com/Benji-9/SmartElevate/issues/6), [#32](https://github.com/Benji-9/SmartElevate/issues/32), [#24](https://github.com/Benji-9/SmartElevate/issues/24), [#25](https://github.com/Benji-9/SmartElevate/issues/25), [#26](https://github.com/Benji-9/SmartElevate/issues/26)
