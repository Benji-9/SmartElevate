# Usuarios, autenticación y prioridad

> Estado: **aprobado** ([#6](https://github.com/Benji-9/SmartElevate/issues/6)). Decisión de diseño en [ADR 0009](../adr/0009-autenticacion-y-prioridad.md).

## Registro

- Solo emails **@uade.edu.ar**, **verificados por link** antes del primer login (proveedor de email: [#25](https://github.com/Benji-9/SmartElevate/issues/25)).
- **Email y legajo únicos.**
- No hay acceso al sistema de UADE: el **legajo es autodeclarado**. La verificación real es la del email institucional.
- El email se guarda en **minúsculas**: `Ana@UADE.edu.ar` y `ana@uade.edu.ar` son la misma cuenta. El legajo son solo dígitos, hasta 10.
- La cuenta nace **común y sin verificar**. El pedido de registro (`POST /api/auth/register`) no tiene campo de rol ni de prioridad, y si el JSON trae alguno se ignora.
- Si el email o el legajo ya tienen cuenta, el registro responde **409** e indica cuál chocó (la pantalla marca ese campo). El login no va a revelar si un email existe.

### Contraseña

Decidido en [#181](https://github.com/Benji-9/SmartElevate/issues/181) para el registro; la de "contraseña nueva" va con [#138](https://github.com/Benji-9/SmartElevate/issues/138).

- **Mínimo 8 caracteres**, sin exigir mayúsculas, números ni símbolos, como recomienda la guía NIST SP 800-63B.
- **Máximo 72 bytes**, el límite de BCrypt, que rechaza con error lo que lo pase. La "ñ" y las tildes ocupan 2 bytes, así que una contraseña con muchas letras acentuadas tiene un tope menor a 72 caracteres.
- Se guarda como **hash BCrypt**. La contraseña nunca se guarda ni se loguea en texto plano, ni siquiera con el log en DEBUG.

### Tipo de usuario declarado

Decidido en [#32](https://github.com/Benji-9/SmartElevate/issues/32).

- En el registro, la persona elige **Estudiante** o **Docente** (`declaredUserType`: `STUDENT` / `TEACHER`, `STUDENT` por defecto). No hay opción "Personal".
- Es **declarativo**: se guarda como dato, pero **no otorga rol ni prioridad**. Todo usuario nace común.
- Si declara **Docente**, queda en la cola **"Docentes a validar"** del panel de administración ([#140](https://github.com/Benji-9/SmartElevate/issues/140)):
  - Si el ADMIN aprueba, obtiene la prioridad `TEACHER`.
  - Si rechaza, sigue como usuario común.
- La prioridad se sigue evaluando **solo en el servidor al reservar**, nunca a partir de este campo.

## Roles y prioridad

- El **rol y la prioridad nunca vienen en el body del registro**: todo usuario nace común.
- Los asigna un **ADMIN** desde el panel de administración.
- **Docentes**: el ADMIN aprueba a los que se declararon docentes ([ver arriba](#tipo-de-usuario-declarado)).
- **Movilidad reducida**: por certificado ([ver abajo](#certificado-de-prioridad)).

Modelo propuesto (a validar al implementar):

| Concepto | Valores |
| --- | --- |
| Rol | `USER`, `REVIEWER` (revisa certificados), `ADMIN` |
| Categoría de prioridad | `NONE`, `TEACHER`, `REDUCED_MOBILITY` |
| Estado de la solicitud de prioridad | `PENDING`, `APPROVED`, `REJECTED`, con fecha de vencimiento |

### Primer ADMIN

No hay endpoint, seed ni migración que cree administradores: así no queda ningún dato del admin en el repo, que es público.

1. La persona se **registra normalmente** con su email @uade.edu.ar y lo **verifica**.
2. Quien administra la base la promueve con un `UPDATE` en el **SQL Editor de Neon**. En dev, lo mismo en `/h2-console`. La sentencia exacta se documenta en [`infraestructura.md`](../infraestructura.md) junto con la migración de usuarios ([#121](https://github.com/Benji-9/SmartElevate/issues/121)).
3. Desde ahí, los demás `ADMIN` y `REVIEWER` los asigna un ADMIN desde el panel.

Render free no permite SSH ni comandos sueltos, así que no hay un comando para correr dentro del servidor.

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
- Implementado en [#120](https://github.com/Benji-9/SmartElevate/issues/120): el JWT lleva solo `sub` (id de usuario); el rol se lee de la base en cada request. El refresh es opaco, de un solo uso y se guarda hasheado; reusar uno ya rotado revoca toda la sesión. Credenciales erróneas dan 401 sin distinguir si el email existe, y una cuenta sin verificar no puede loguearse (403, solo tras acertar la clave).
- La **prioridad se evalúa en el servidor al reservar**, consultando el estado actual. **No se usa un claim del token**: la prioridad puede vencer o revocarse mientras el token sigue vigente.

## Datos sensibles

Un certificado médico o de discapacidad es un **dato de salud**. La **Ley 25.326** (Protección de Datos Personales) los trata como datos sensibles: exige **consentimiento expreso**, **finalidad limitada** y **medidas de seguridad**.

> Esto **no es asesoramiento legal**. La normativa vigente la tiene que verificar el equipo, por ejemplo consultando a la cátedra: [#24](https://github.com/Benji-9/SmartElevate/issues/24).

Diseño propuesto:

- **Consentimiento explícito** al subir el archivo: checkbox con el texto de finalidad, y registro de cuándo se aceptó.
- Guardarlo en un **bucket privado** (proveedor: [#26](https://github.com/Benji-9/SmartElevate/issues/26)), accesible **solo por el rol `REVIEWER`**, mediante **URLs firmadas de corta duración**.
- **Borrar el archivo apenas se resuelve** la solicitud, y conservar **solo el estado y el vencimiento**.
- No loguear el contenido ni el nombre original del archivo.
