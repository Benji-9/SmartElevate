import type { ApiErrorBody, Building, FieldViolation, PingResponse } from '../types/api';
import type {
  AdminKpis,
  AdminKpisQuery,
  AdminShift,
  CheckInRequest,
  CheckInResult,
  CongestionSnapshot,
  Core,
  Departure,
  FloorOption,
  ForgotPasswordRequest,
  LoginRequest,
  NoShowStatus,
  NotificationPreferences,
  PriorityRequest,
  PriorityUploadRules,
  RegisterRequest,
  Reservation,
  ReserveRequest,
  ResetPasswordRequest,
  Session,
  TripPage,
  User,
  WaitFeedbackRequest,
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
      // Con FormData el navegador pone el Content-Type (multipart con su boundary).
      ...(init?.body && !(init.body instanceof FormData)
        ? { 'Content-Type': 'application/json' }
        : {}),
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

  // 204, o 202 sin cuerpo (p. ej. /auth/password/forgot): no hay JSON que leer.
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
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

/** Manda el link para elegir otra contraseña. No revela si el email tiene cuenta (siempre 202). */
export const requestPasswordReset = (body: ForgotPasswordRequest) =>
  api.post<void>('/auth/password/forgot', body);
/** Guarda la contraseña nueva con el token del mail. 400 si el token venció o ya se usó. */
export const resetPassword = (body: ResetPasswordRequest) =>
  api.post<void>('/auth/password/reset', body);

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
/** Encuesta de espera, opcional y una sola vez por check-in. */
export const sendWaitFeedback = (reservationId: string, body: WaitFeedbackRequest) =>
  api.post<void>(`/check-ins/${encodeURIComponent(reservationId)}/wait-feedback`, body);
export const getMyPriorityRequest = () => api.get<PriorityRequest | null>('/priority-requests/me');
export const getPriorityUploadRules = () =>
  api.get<PriorityUploadRules>('/priority-requests/upload-rules');
/**
 * Pide acceso prioritario por movilidad reducida. El certificado es un dato de salud
 * (Ley 25.326): va solo en esta llamada y nunca se vuelve a pedir ni mostrar.
 */
export function submitPriorityRequest(certificate: File, consentAccepted: true) {
  const form = new FormData();
  form.append('category', 'REDUCED_MOBILITY');
  form.append('consentAccepted', String(consentAccepted));
  form.append('certificate', certificate);
  return request<PriorityRequest>('/priority-requests', { method: 'POST', body: form });
}
/** Historial de viajes paginado; sin `cursor` trae la primera página. */
export const getTrips = (cursor?: string | null) =>
  api.get<TripPage>(
    `/reservations/history${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`,
  );
export const getNoShowStatus = () => api.get<NoShowStatus>('/me/no-shows');
export const getNotificationPreferences = () =>
  api.get<NotificationPreferences>('/me/notification-preferences');
export const saveNotificationPreferences = (body: NotificationPreferences) =>
  api.put<NotificationPreferences>('/me/notification-preferences', body);
/** KPIs del panel admin. Solo rol ADMIN (lo valida el backend). */
export function getAdminKpis({ period, buildingId, shiftId }: AdminKpisQuery) {
  const query = new URLSearchParams({
    period,
    ...(buildingId ? { buildingId } : {}),
    ...(shiftId ? { shiftId } : {}),
  });
  return api.get<AdminKpis>(`/admin/kpis?${query}`);
}
/** Turnos de cursada para filtrar los KPIs. */
export const getAdminShifts = () => api.get<AdminShift[]>('/admin/shifts');
