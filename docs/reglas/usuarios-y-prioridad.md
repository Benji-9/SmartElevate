# Usuarios, autenticación y prioridad

> Estado: **propuesta** para cerrar [#6](https://github.com/Benji-9/SmartElevate/issues/6). Decisión de diseño en [ADR 0009](../adr/0009-autenticacion-y-prioridad.md).

## Registro

- Solo emails **@uade.edu.ar**, **verificados por link** antes del primer login (proveedor de email: [#25](https://github.com/Benji-9/SmartElevate/issues/25)).
- **Email y legajo únicos.**
- No hay acceso al sistema de UADE: el **legajo es autodeclarado**. La verificación real es la del email institucional.

## Roles y prioridad

- El **rol y la prioridad nunca vienen en el body del registro**: todo usuario nace común.
- Los asigna un **ADMIN**.
- **Docentes**: aprobación manual del ADMIN o carga de una lista de docentes.
- **Movilidad reducida**: por certificado ([ver abajo](#certificado-de-prioridad)).

Modelo propuesto (a validar al implementar):

| Concepto | Valores |
| --- | --- |
| Rol | `USER`, `REVIEWER` (revisa certificados), `ADMIN` |
| Categoría de prioridad | `NONE`, `TEACHER`, `REDUCED_MOBILITY` |
| Estado de la solicitud de prioridad | `PENDING`, `APPROVED`, `REJECTED`, con fecha de vencimiento |

## Certificado de prioridad

```
PENDIENTE ──► APROBADO (con vencimiento) ──► vence ──► usuario común
    └──────► RECHAZADO
```

- Todo **APROBADO** tiene **fecha de vencimiento**. Las lesiones temporales vencen rápido.
- Al vencer, el usuario **vuelve a ser común** automáticamente.

### Archivos

- Validar el **tipo por contenido** (magic bytes), no por extensión.
- **Tope de tamaño** (propuesta: 5 MB).
- Formatos propuestos: PDF, JPG, PNG.

## Sesión (JWT)

- **Access token corto (~15 min) más refresh token.**
- La **prioridad se evalúa en el servidor al reservar**, consultando el estado actual. **No se usa un claim del token**: la prioridad puede vencer o revocarse mientras el token sigue vigente.

## Datos sensibles

Un certificado médico o de discapacidad es un **dato de salud**. La **Ley 25.326** (Protección de Datos Personales) los trata como datos sensibles: exige **consentimiento expreso**, **finalidad limitada** y **medidas de seguridad**.

> Esto **no es asesoramiento legal**. La normativa vigente la tiene que verificar el equipo, por ejemplo consultando a la cátedra: [#24](https://github.com/Benji-9/SmartElevate/issues/24).

Diseño propuesto:

- **Consentimiento explícito** al subir el archivo: checkbox con el texto de finalidad, y registro de cuándo se aceptó.
- Guardarlo en un **bucket privado** (proveedor: [#26](https://github.com/Benji-9/SmartElevate/issues/26)), accesible **solo por el rol `REVIEWER`**, mediante **URLs firmadas de corta duración**.
- **Borrar el archivo apenas se resuelve** la solicitud, y conservar **solo el estado y el vencimiento**.
- No loguear el contenido ni el nombre original del archivo.
