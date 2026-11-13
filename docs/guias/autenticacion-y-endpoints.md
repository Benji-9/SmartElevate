# Autenticación y endpoints protegidos

Cómo funciona la sesión y cómo agregar un endpoint (público, protegido o por rol) en el backend.

`POST /api/auth/login` devuelve un access token (JWT, 15 min, solo con `sub`: **sin rol ni prioridad**) y un refresh token opaco de un solo uso (se guarda hasheado) que va **solo** en una cookie `HttpOnly; Secure; SameSite=Lax` (`Path=/api/auth`), no en el body (ADR 0019). `POST /api/auth/refresh` lo lee de la cookie y lo rota; si un refresh ya rotado reaparece, se revoca toda la sesión. `POST /api/auth/logout` lo revoca y expira la cookie. Ambos exigen el header `X-Requested-With: SmartElevate` y, si viene `Origin`, que esté en `CORS_ALLOWED_ORIGINS` (defensa CSRF, ADR 0019). `CORS_ALLOWED_ORIGINS` se compara **exacto** con el `Origin`: sin barra final y en minúsculas (`https://smart-elevate.vercel.app`). Un refresh inválido responde 401 y además expira la cookie. El rol se lee de la base en cada request y la prioridad se consulta al reservar (ADR 0009). No se puede loguear una cuenta con el email sin verificar.

Públicos: `/api/ping`, `/api/auth/**`, `GET /api/elevators`, `GET /api/buildings`, la documentación (`/v3/api-docs`, `/swagger-ui.html`) y `/actuator/health`. **Todo lo demás exige `Authorization: Bearer <accessToken>`**. Para agregar un endpoint:

- Protegido: no hay que hacer nada, es el default. Por rol, `@PreAuthorize("hasRole('ADMIN')")` en el método (sin token da 401, con otro rol 403).
- Usuario actual: parámetro `@AuthenticationPrincipal CurrentUser user` (`common/security`).
- Documentarlo en OpenAPI: `@SecurityRequirement(name = OpenApiConfig.BEARER)` en el controller.
- Público: sumarlo a `SecurityConfig.PUBLIC_ANY` / `PUBLIC_GET`, solo con motivo.
- Tests `@WebMvcTest`: anotar con `@WithAppSecurity` (carga las reglas reales) y usar `jwt()` de `spring-security-test`.

Roles y prioridad (#121): `GET/POST /api/admin/teachers/...`, `GET/DELETE /api/admin/priority-users/...` y `PUT /api/admin/users/{id}/role` son solo ADMIN. La prioridad vigente (aprobada y no vencida) la da `PriorityService.currentPriority(userId)`, el único punto que debe usar la reserva; cada cambio queda en la tabla `priority_audit`. Cómo crear el primer ADMIN: [`docs/infraestructura.md`](../infraestructura.md#crear-el-primer-admin).

Kioscos del QR (#229, [ADR 0020](../adr/0020-autenticacion-de-kioscos-por-credencial-de-dispositivo.md)): `GET/POST /api/admin/kiosks` y `POST /api/admin/kiosks/{id}/revoke` son solo ADMIN; el alta devuelve el secreto del kiosco **una sola vez**. `/api/kiosks/**` tiene su propia cadena de seguridad (`KioskSecurityConfig`): se autentica con `Authorization: Bearer <secreto del kiosco>`, no con un JWT. Las altas y revocaciones quedan en `priority_audit`.
