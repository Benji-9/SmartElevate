import type { ApiErrorBody, PingResponse } from '../types/api';
import type {
  AdminKpis,
  Building,
  CheckInRequest,
  CheckInResult,
  Core,
  Departure,
  LoginRequest,
  PriorityRequest,
  Reservation,
  ReserveRequest,
  Session,
  User,
} from '../types/pending';

const BASE_URL = (import.meta.env.VITE_API_URL || '/api').replace(/\/+$/, '');

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

// Datos simulados (#42): prendidos por defecto en `vite dev`, apagados en tests y siempre
// fuera del build de producción (DEV es false ahí y Vite descarta el import).
const USE_MOCKS =
  import.meta.env.DEV &&
  (import.meta.env.VITE_USE_MOCKS ?? String(import.meta.env.MODE === 'development')) === 'true';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  if (USE_MOCKS) {
    const { mockRequest } = await import('./mocks');
    const mocked = await mockRequest(init?.method ?? 'GET', path, init?.body);
    if (mocked) return mocked.data as T;
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    let message = response.statusText;
    try {
      const body = (await response.json()) as Partial<ApiErrorBody>;
      message = body.message ?? message;
    } catch {
      // El cuerpo no es JSON: nos quedamos con statusText.
    }
    throw new ApiError(response.status, message);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string, init?: RequestInit) => request<T>(path, { ...init, method: 'GET' }),
  post: <T>(path: string, body: unknown, init?: RequestInit) =>
    request<T>(path, { ...init, method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown, init?: RequestInit) =>
    request<T>(path, { ...init, method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string, init?: RequestInit) => request<T>(path, { ...init, method: 'DELETE' }),
};

export const ping = (signal?: AbortSignal) => api.get<PingResponse>('/ping', { signal });

// Endpoints todavía sin backend: por ahora los responde services/mocks.ts.
export const login = (body: LoginRequest) => api.post<Session>('/auth/login', body);
export const getMe = () => api.get<User>('/me');
export const getBuildings = () => api.get<Building[]>('/buildings');
export const getCores = () => api.get<Core[]>('/cores');
export const getDepartures = (coreId: string) =>
  api.get<Departure[]>(`/cores/${encodeURIComponent(coreId)}/departures`);
export const getActiveReservation = () => api.get<Reservation | null>('/reservations/active');
export const reserve = (body: ReserveRequest) => api.post<Reservation>('/reservations', body);
export const cancelReservation = (id: string) =>
  api.delete<void>(`/reservations/${encodeURIComponent(id)}`);
export const checkIn = (body: CheckInRequest) => api.post<CheckInResult>('/check-ins', body);
export const getMyPriorityRequest = () => api.get<PriorityRequest | null>('/priority-requests/me');
export const getAdminKpis = () => api.get<AdminKpis>('/admin/kpis');
