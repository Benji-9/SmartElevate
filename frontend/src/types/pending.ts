// PROVISORIO: DTOs que todavía no están en docs/openapi.json.
// Los usan los datos simulados (services/mocks.ts) mientras no exista el backend.
// Cuando el endpoint real esté, regenerar con `npm run gen:api`, exponer el tipo en
// types/api.ts y borrarlo de acá.
// Backend pendiente: auth y usuarios #6, turnos #7, ascensores y edificios #22,
// tipo de usuario declarativo #32.

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
};

export type LoginRequest = { email: string; password: string };
export type Session = { accessToken: string; user: User };

export type Building = { id: string; name: string; minFloor: number; maxFloor: number };

/** Núcleo de ascensores (L1, IND2…) con su congestión actual. */
export type Core = {
  id: string;
  buildingId: string;
  name: string;
  floors: number[];
  congestion: CongestionLevel;
  estimatedWaitMinutes: number;
};

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
  originFloor: number;
  destinationFloor: number;
};

/** Código del QR escaneado o ingresado a mano. */
export type CheckInRequest = { code: string };
export type CheckInOutcome = 'ON_TIME' | 'OTHER_ELEVATOR' | 'LATE';
export type CheckInResult = {
  reservationId: string;
  outcome: CheckInOutcome;
  checkedInAt: string;
  /** Espera real − estimada, en segundos (negativo si fue antes). */
  waitDeltaSeconds: number;
};

export type PriorityRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type PriorityRequest = {
  category: Exclude<PriorityCategory, 'NONE'>;
  status: PriorityRequestStatus;
  submittedAt: string;
  /** Solo si está aprobada. */
  expiresAt: string | null;
};

export type AdminKpis = {
  /** Punto de partida de la encuesta: % que espera 5–10 min. */
  baselinePercent: number;
  avgWaitDeltaSeconds: number;
  occupancyPercent: number;
  qrCompliancePercent: number;
  noShowsToday: number;
  priorityAvgWaitSeconds: number;
  cores: { coreId: string; name: string; congestion: CongestionLevel; occupancyPercent: number }[];
};
