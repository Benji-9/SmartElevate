import type { ApiErrorBody, FieldViolation, PingResponse } from '../types/api';
import type {
  AdminKpis,
  Building,
  CheckInRequest,
  CheckInResult,
  CongestionSnapshot,
  Core,
  Departure,
  FloorOption,
  LoginRequest,
  PriorityRequest,
  RegisterRequest,
  Reservation,
  ReserveRequest,
  Session,
  User,
} from '../types/pending';

const BASE_URL = (import.meta.env.VITE_API_URL || '/api').replace(/\/+$/, '');

export class ApiError extends Error {
  readonly status: number;
  /** Errores por campo, si el backend los manda (validación, email o legajo duplicado). */
  readonly violations: FieldViolation[];

  constructor(status: number, message: string, violations: FieldViolation[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.violations = violations;
  }
}

// Datos simulados (#42): prendidos por defecto en `vite dev`, apagados en tests y siempre
// fuera del build de producción (DEV es false ahí y Vite descarta el import).
const USE_MOCKS =
  import.meta.env.DEV &&
  (import.meta.env.VITE_USE_MOCKS ?? String(import.meta.env.MODE === 'development')) === 'true';

// Sesión (#45): el access token vive solo en memoria. El refresh token lo maneja el backend
// en una cookie httpOnly (propuesta, a confirmar en #6): el frontend nunca lo lee.
let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
const expiredListeners = new Set<() => void>();

/** Avisa cuando la sesión venció y no se pudo renovar. Devuelve la función para desuscribirse. */
export function onSessionExpired(listener: () => void) {
  expiredListeners.add(listener);
  return () => {
    expiredListeners.delete(listener);
  };
}

async function send<T>(path: string, init?: RequestInit): Promise<T> {
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
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    let message = response.statusText;
    let violations: FieldViolation[] = [];
    try {
      const body = (await response.json()) as Partial<ApiErrorBody>;
      message = body.message ?? message;
      violations = body.violations ?? [];
    } catch {
      // El cuerpo no es JSON: nos quedamos con statusText.
    }
    throw new ApiError(response.status, message, violations);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** Pide un access token nuevo con la cookie de refresh. Las llamadas simultáneas comparten el intento. */
function refreshAccessToken(): Promise<boolean> {
  refreshing ??= send<Session>('/auth/refresh', { method: 'POST', credentials: 'include' })
    .then(
      (session) => {
        accessToken = session.accessToken;
        return true;
      },
      () => {
        accessToken = null;
        return false;
      },
    )
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  try {
    return await send<T>(path, init);
  } catch (error) {
    const expired =
      error instanceof ApiError &&
      error.status === 401 &&
      accessToken !== null &&
      !path.startsWith('/auth/');
    if (!expired) throw error;
    // Un solo intento de renovación; si falla, la sesión terminó.
    if (await refreshAccessToken()) return send<T>(path, init);
    expiredListeners.forEach((listener) => listener());
    throw error;
  }
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
export const getMe = () => api.get<User>('/me');

/** Crea la cuenta; queda pendiente de verificar por email. */
export const register = (body: RegisterRequest) => api.post<void>('/auth/register', body);

/** Inicia sesión y devuelve el usuario según `/me` (rol y prioridad los decide el servidor). */
export async function login(body: LoginRequest): Promise<User> {
  const session = await api.post<Session>('/auth/login', body, { credentials: 'include' });
  accessToken = session.accessToken;
  return getMe();
}

/** Recupera la sesión al abrir la app (cookie de refresh). `null` si no hay sesión. */
export async function restoreSession(): Promise<User | null> {
  if (!(await refreshAccessToken())) return null;
  return getMe().catch(() => null);
}

export async function logout(): Promise<void> {
  try {
    await api.post<void>('/auth/logout', undefined, { credentials: 'include' });
  } catch {
    // Aunque el backend no responda, la sesión local se cierra igual.
  } finally {
    accessToken = null;
  }
}
export const getBuildings = () => api.get<Building[]>('/buildings');
export const getCores = () => api.get<Core[]>('/cores');
export const getDepartures = (coreId: string) =>
  api.get<Departure[]>(`/cores/${encodeURIComponent(coreId)}/departures`);
export const getCongestion = () => api.get<CongestionSnapshot>('/congestion');
/** Pisos de destino desde `origin`, con su elegibilidad para el usuario actual. */
export const getDestinationFloors = (coreId: string, origin: number) =>
  api.get<FloorOption[]>(`/cores/${encodeURIComponent(coreId)}/floors?origin=${origin}`);
export const getActiveReservation = () => api.get<Reservation | null>('/reservations/active');
export const getReservation = (id: string) =>
  api.get<Reservation>(`/reservations/${encodeURIComponent(id)}`);
export const reserve = (body: ReserveRequest) => api.post<Reservation>('/reservations', body);
export const cancelReservation = (id: string) =>
  api.delete<void>(`/reservations/${encodeURIComponent(id)}`);
export const checkIn = (body: CheckInRequest) => api.post<CheckInResult>('/check-ins', body);
export const getMyPriorityRequest = () => api.get<PriorityRequest | null>('/priority-requests/me');
export const getAdminKpis = () => api.get<AdminKpis>('/admin/kpis');
