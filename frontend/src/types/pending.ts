// PROVISORIO: DTOs que todavía no están en docs/openapi.json.
// Los usan los datos simulados (services/mocks.ts) mientras no exista el backend.
// Cuando el endpoint real esté, regenerar con `npm run gen:api`, exponer el tipo en
// types/api.ts y borrarlo de acá.
// Backend pendiente: auth y usuarios #6, turnos #7, ascensores y edificios #22,
// tipo de usuario declarativo #32, check-in #23, certificados #24 y #26.

/** Nivel de congestión de un núcleo. */
export type CongestionLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export type Role = 'USER' | 'REVIEWER' | 'ADMIN';
export type PriorityCategory = 'NONE' | 'TEACHER' | 'REDUCED_MOBILITY';

export type User = {
  id: string;
  email: string;
  fullName: string;
  legajo: string;
  role: Role;
  /** Prioridad vigente, solo para mostrar. El servidor la evalúa al reservar. */
  priority: PriorityCategory;
  /** Lo que declaró al registrarse (informativo, no da rol ni prioridad). */
  declaredUserType: DeclaredUserType;
};

export type LoginRequest = { email: string; password: string };

/**
 * Tipo de usuario que la persona declara al registrarse (#32). No otorga rol ni prioridad:
 * un docente declarado queda pendiente hasta que un ADMIN lo aprueba desde el panel.
 */
export type DeclaredUserType = 'STUDENT' | 'TEACHER';

export type RegisterRequest = {
  fullName: string;
  email: string;
  legajo: string;
  password: string;
  declaredUserType: DeclaredUserType;
};
/** Pide el link para elegir otra contraseña (#138). El servidor responde 202 siempre. */
export type ForgotPasswordRequest = { email: string };
/** `token` viene en el link del mail; es de un solo uso y vence (#138). */
export type ResetPasswordRequest = { token: string; newPassword: string };
/** El refresh token viaja en una cookie httpOnly (propuesta, #6): no está en el body. */
export type Session = { accessToken: string };

/** Núcleo de ascensores (L1, IND2…) con su congestión actual. */
export type Core = {
  id: string;
  /** Código del edificio (`Building.code`). */
  buildingId: string;
  name: string;
  floors: number[];
  congestion: CongestionLevel;
  estimatedWaitMinutes: number;
  /** Dónde esperar el ascensor (p. ej. "Hall Lima, planta baja"). */
  hall: string;
};

export type CoreCongestion = {
  coreId: string;
  name: string;
  level: CongestionLevel;
  estimatedWaitMinutes: number;
};

/** Congestión actual por núcleo. El servidor dice cada cuánto volver a consultarla. */
export type CongestionSnapshot = {
  updatedAt: string;
  refreshAfterSeconds: number;
  cores: CoreCongestion[];
};

/**
 * Piso de destino posible para un origen dado. La elegibilidad (regla de pisos bajos,
 * exención por movilidad reducida) la decide el servidor; `reason` explica por qué no.
 */
export type FloorOption = { floor: number; eligible: boolean; reason: string | null };

/** Salida de ascensor (la franja que se reserva). `departsAt` en UTC (ISO 8601). */
export type Departure = {
  id: string;
  coreId: string;
  departsAt: string;
  durationMinutes: number;
  capacity: number;
  occupied: number;
};

export type ReservationStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

export type ReserveRequest = { departureId: string; originFloor: number; destinationFloor: number };

export type Reservation = {
  id: string;
  status: ReservationStatus;
  departure: Departure;
  core: { id: string; name: string; buildingName: string; floors: number[]; hall: string };
  originFloor: number;
  destinationFloor: number;
  /** `true` si cancelar ahora cuenta como falta (pasado el límite). Lo calcula el servidor. */
  cancelCountsAsNoShow: boolean;
};

/**
 * Token del QR escaneado o código corto tipeado a mano (rota igual que el QR y está atado
 * al ascensor; a confirmar con backend, #23). El JWT de la sesión identifica al usuario.
 * Errores: 400 código inválido o vencido, 409 sin turno activo o check-in ya registrado.
 */
export type CheckInRequest = { code: string };
/** Cumplida / otro ascensor / fuera de hora (docs/reglas/check-in-qr.md#resultados). */
export type CheckInOutcome = 'ON_TIME' | 'OTHER_ELEVATOR' | 'LATE';
export type CheckInResult = {
  reservationId: string;
  outcome: CheckInOutcome;
  checkedInAt: string;
  /** Espera real − estimada, en segundos (negativo si fue antes). */
  waitDeltaSeconds: number;
  /** Ascensor donde se escaneó (puede no ser el del turno) y su núcleo. */
  elevatorName: string;
  coreName: string;
  /** Salida reservada (UTC), para mostrar la franja. */
  departsAt: string;
  durationMinutes: number;
};

/** Rangos de la encuesta "¿Cuánto esperaste?" (mismos que el baseline, kpis.md). */
export type WaitRange = 'UNDER_2' | 'FROM_2_TO_5' | 'FROM_5_TO_10' | 'OVER_10';
/** Una sola vez por check-in: la segunda da 409. */
export type WaitFeedbackRequest = { range: WaitRange };

export type PriorityRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type PriorityRequest = {
  category: Exclude<PriorityCategory, 'NONE'>;
  status: PriorityRequestStatus;
  submittedAt: string;
  /** Solo si está aprobada. */
  expiresAt: string | null;
};

/**
 * Límites del certificado que informa el servidor. Es solo para validar antes de subir:
 * la validación real (magic bytes) la hace el backend.
 */
export type PriorityUploadRules = {
  maxSizeBytes: number;
  /** MIME aceptados, p. ej. `application/pdf`. */
  acceptedTypes: string[];
};

/**
 * Viaje del historial. `result` combina el estado de la reserva con el resultado del
 * check-in: COMPLETED = cumplido a tiempo.
 */
export type TripResult = 'COMPLETED' | 'OTHER_ELEVATOR' | 'LATE' | 'CANCELLED' | 'NO_SHOW';
export type Trip = {
  id: string;
  departsAt: string;
  durationMinutes: number;
  coreName: string;
  originFloor: number;
  destinationFloor: number;
  result: TripResult;
};
/** Página del historial, del más reciente al más viejo. `nextCursor` null = no hay más. */
export type TripPage = { items: Trip[]; nextCursor: string | null };

/** Faltas recientes (turnos.md §5). Los prioritarios no se suspenden: `exempt`. */
export type NoShowStatus = {
  recentNoShows: number;
  /** Parámetros de la regla: N faltas en `windowDays` días suspenden. */
  threshold: number;
  windowDays: number;
  suspensionHours: number;
  suspendedUntil: string | null;
  exempt: boolean;
};

/** Qué avisos quiere recibir el usuario. */
export type NotificationPreferences = {
  departureReminder: boolean;
  spotReleased: boolean;
  delayCancellation: boolean;
  priorityAccess: boolean;
};

/** Período del panel admin; el servidor lo resuelve en hora de Buenos Aires. */
export type AdminPeriod = 'TODAY' | 'WEEK' | 'MONTH';
/**
 * Turno de cursada (kpis.md#filtros). Lo define la tabla de configuración; horas `"HH:mm"`
 * en Buenos Aires, `endsAt` exclusivo.
 */
export type AdminShift = { id: string; name: string; startsAt: string; endsAt: string };
/** `buildingId` ausente = todas las sedes; `shiftId` ausente = todo el día. */
export type AdminKpisQuery = { period: AdminPeriod; buildingId?: string; shiftId?: string };

/** Estado de un núcleo en el período (tabla "Estado por núcleo"). */
export type CoreKpis = {
  coreId: string;
  name: string;
  reservations: number;
  /** Personas por salida / capacidad, en %. */
  occupancyPercent: number;
  avgWaitSeconds: number;
  congestion: CongestionLevel;
};

/**
 * KPIs de congestión (docs/reglas/kpis.md) para el período y la sede pedidos. Todo lo
 * calcula el servidor; el frontend solo formatea.
 */
export type AdminKpis = {
  /** Rango del período, instantes UTC (`to` exclusivo). */
  from: string;
  to: string;
  /** Espera real promedio (check-in − salida estimada). */
  avgWaitSeconds: number;
  /** % de respuestas de la encuesta de espera en 5–10 min, comparable con la línea base. */
  wait5To10Percent: number;
  /** Línea base de la encuesta inicial: % que esperaba 5–10 min (43,5). */
  baselinePercent: number;
  /** Reservas no canceladas. */
  reservations: number;
  /** Check-ins cumplidos y % sobre las reservas no canceladas. */
  checkIns: number;
  checkInPercent: number;
  /** Personas promedio por salida sobre `capacity`. */
  avgOccupancy: number;
  capacity: number;
  /** Reservas por hora del día (0–23, Buenos Aires) sumadas en el período, en orden. */
  reservationsByHour: { hour: number; reservations: number }[];
  cores: CoreKpis[];
};
