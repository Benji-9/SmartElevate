// Tipos de la API derivados del contrato OpenAPI (docs/openapi.json).
// No definir DTOs a mano: regenerar con `npm run gen:api` y exponerlos acá con un alias.
import type { components, operations } from './openapi';

type Schemas = components['schemas'];

export type PingResponse = Schemas['PingResponse'];

/** Formato de error que devuelve el backend (GlobalExceptionHandler). */
export type ApiErrorBody = Schemas['ApiError'];

/** Error de un campo puntual (validación o dato duplicado). */
export type FieldViolation = Schemas['FieldViolation'];

/** Edificio con su rango de pisos y sus núcleos (código y nombre visible, p. ej. "Lima 1"). */
export type Building = Schemas['BuildingResponse'];

export type LoginRequest = Schemas['LoginRequest'];
export type RefreshRequest = Schemas['RefreshRequest'];
/** Token del link de verificación de email. */
export type VerifyEmailRequest = Schemas['VerifyEmailRequest'];
export type ResendVerificationRequest = Schemas['ResendVerificationRequest'];

/** Usuario de la sesión (`GET /users/me`). Rol y prioridad los decide el servidor, nunca el JWT (ADR 0009). */
export type User = Schemas['UserResponse'];
export type Role = User['role'];
/** Prioridad vigente, solo para mostrar: el servidor la evalúa al reservar. */
export type PriorityCategory = User['priority'];
/** Lo que la persona declara al registrarse (#32). No otorga rol ni prioridad. */
export type DeclaredUserType = User['declaredUserType'];

export type RegisterRequest = Schemas['RegisterRequest'];
/** Respuesta de `POST /auth/login` y `/auth/refresh`. El access token no lleva rol ni prioridad. */
export type TokenResponse = Schemas['TokenResponse'];
/** Pide el link para elegir otra contraseña (#138). El servidor responde 202 siempre. */
export type ForgotPasswordRequest = Schemas['ForgotPasswordRequest'];
/** `token` viene en el link del mail; es de un solo uso y vence (#138). */
export type ResetPasswordRequest = Schemas['ResetPasswordRequest'];

/** Panel admin de prioridad (#140): docente declarado que espera aprobación; `registeredAt` en UTC. */
export type PendingTeacher = Schemas['PendingTeacherResponse'];
/** Usuario con prioridad vigente (`TEACHER` o `REDUCED_MOBILITY`). */
export type PriorityUser = Schemas['PriorityUserResponse'];

/** Ascensor con sus pisos (`GET /elevators`). */
export type Elevator = Schemas['ElevatorResponse'];

/** Kioscos del QR (#229, ADR 0020). La lista nunca trae secretos. */
export type KioskList = Schemas['KioskListResponse'];
export type Kiosk = Schemas['KioskResponse'];
export type CreateKioskRequest = Schemas['CreateKioskRequest'];
/** Respuesta del alta: única vez que la API devuelve `secret`. */
export type CreatedKiosk = Schemas['CreatedKioskResponse'];
/** Token que el kiosco muestra como QR; a `expiresAt` pide el siguiente. `elevatorId` es el de la credencial. */
export type QrToken = Schemas['QrTokenResponse'];

/** Check-in (#230): el token del QR tal cual se leyó; el usuario sale de la sesión. */
export type CheckInRequest = Schemas['CheckInRequest'];
/** Resultado del check-in con el ascensor escaneado y el reservado (docs/reglas/check-in-qr.md). */
export type CheckIn = Schemas['CheckInResponse'];
export type CheckInOutcome = CheckIn['outcome'];

/** Faltas recientes (`GET /me/no-shows`, turnos.md §5). Los prioritarios no se suspenden: `exempt`. */
export type NoShowStatus = Schemas['NoShowStatusResponse'];

/**
 * Salida reservable de un ascensor (`GET /departures`). La lista ya viene filtrada por usuario
 * (ascensores que puede usar) y `remainingSeats` son los lugares que él puede tomar.
 */
export type Departure = Schemas['DepartureResponse'];
export type ReserveRequest = Schemas['ReserveRequest'];
/** Reserva (`POST /reservations`, `GET /reservations/me`). `id` y `departureId` son numéricos. */
export type Reservation = Schemas['ReservationResponse'];
export type ReservationStatus = Reservation['status'];

/** Congestión actual por núcleo (#220). El servidor dice cada cuánto volver a consultarla. */
export type CongestionSnapshot = Schemas['CongestionResponse'];
export type CoreCongestion = Schemas['CoreCongestion'];
/** Nivel de congestión de un núcleo; los umbrales los fija el servidor (app_config). */
export type CongestionLevel = CoreCongestion['level'];

/**
 * KPIs de congestión del panel admin (docs/reglas/kpis.md). Todo lo calcula el servidor; los
 * `null` son KPIs sin datos (la espera real necesita la encuesta, fuera del MVP).
 */
export type AdminKpis = Schemas['AdminKpisResponse'];
/** Totales del período anterior de igual largo, para comparar (#180). */
export type PreviousKpis = Schemas['PreviousKpis'];
/** Estado de un núcleo en el período (tabla "Estado por núcleo"). */
export type CoreKpis = Schemas['CoreKpis'];
/** Turno de cursada (kpis.md#filtros): horas `"HH:mm"` en Buenos Aires, `endsAt` exclusivo. */
export type AdminShift = Schemas['AdminShiftResponse'];
/** Filtros de `GET /admin/kpis`: sin `buildingId`, todas las sedes; sin `shiftId`, todo el día. */
export type AdminKpisQuery = operations['kpis']['parameters']['query'];
/** Período del panel admin; el servidor lo resuelve en hora de Buenos Aires. */
export type AdminPeriod = AdminKpisQuery['period'];
