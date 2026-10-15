// PROVISORIO: DTOs que todavía no están en docs/openapi.json.
// Los usan los datos simulados (services/mocks.ts) mientras no exista el backend.
// Cuando el endpoint real esté, regenerar con `npm run gen:api`, exponer el tipo en
// types/api.ts y borrarlo de acá.
// Backend pendiente: turnos #7, ascensores y edificios #22,
// tipo de usuario declarativo #32, certificados #24 y #26.
import type { PriorityCategory } from './api';

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

/** Qué avisos quiere recibir el usuario. */
export type NotificationPreferences = {
  departureReminder: boolean;
  spotReleased: boolean;
  delayCancellation: boolean;
  priorityAccess: boolean;
};
