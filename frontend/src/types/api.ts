// Tipos de la API derivados del contrato OpenAPI (docs/openapi.json).
// No definir DTOs a mano: regenerar con `npm run gen:api` y exponerlos acá con un alias.
import type { components } from './openapi';

type Schemas = components['schemas'];

export type PingResponse = Schemas['PingResponse'];

/** Formato de error que devuelve el backend (GlobalExceptionHandler). */
export type ApiErrorBody = Schemas['ApiError'];

/** Error de un campo puntual (validación o dato duplicado). */
export type FieldViolation = Schemas['FieldViolation'];
