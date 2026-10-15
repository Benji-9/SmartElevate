// PROVISORIO: DTOs que todavía no están en docs/openapi.json.
// Los usan los datos simulados (services/mocks.ts) mientras no exista el backend.
// Cuando el endpoint real esté, regenerar con `npm run gen:api`, exponer el tipo en
// types/api.ts y borrarlo de acá.
// Backend pendiente: turnos #7, ascensores y edificios #22,
// tipo de usuario declarativo #32, certificados #24 y #26.
import type { PriorityCategory } from './api';

// Forma vieja del turno: la usan Inicio, Turno y Check-in con `GET /reservations/active` y
// `GET /reservations/{id}`, que no están en el contrato. La migra #214 a `Reservation` de
// types/api.ts (`GET /reservations/me`); Reservar ya usa la del contrato.
export type ReservationStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

export type Reservation = {
  id: string;
  status: ReservationStatus;
  departure: {
    id: string;
    coreId: string;
    departsAt: string;
    durationMinutes: number;
    capacity: number;
    occupied: number;
  };
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
