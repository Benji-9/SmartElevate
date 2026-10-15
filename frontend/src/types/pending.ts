// PROVISORIO: DTOs que todavía no están en docs/openapi.json.
// Los usan los datos simulados (services/mocks.ts) mientras no exista el backend.
// Cuando el endpoint real esté, regenerar con `npm run gen:api`, exponer el tipo en
// types/api.ts y borrarlo de acá.
// Backend pendiente: turnos #7, ascensores y edificios #22,
// tipo de usuario declarativo #32, certificados #24 y #26.
import type { PriorityCategory } from './api';

/** Nivel de congestión de un núcleo. */
export type CongestionLevel = 'LOW' | 'MEDIUM' | 'HIGH';

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
