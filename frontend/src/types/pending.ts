// PROVISORIO: solo lo que todavía NO está en docs/openapi.json. Todo lo demás vive en types/api.ts.
// Certificados de prioridad por movilidad reducida: los endpoints (`/priority-requests*`) dependen
// del backend de certificados (#122, #123) y de la decisión sobre datos de salud (#24). Mientras
// tanto solo los usan los datos simulados (services/mocks.ts). Cuando existan en el contrato:
// `npm run gen:api`, mover los tipos a types/api.ts y borrar este archivo.
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
