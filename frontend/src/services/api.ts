import type {
  ApiErrorBody,
  Building,
  FieldViolation,
  LoginRequest,
  PingResponse,
  RefreshRequest,
  ResendVerificationRequest,
  VerifyEmailRequest,
} from '../types/api';
import type {
  AdminKpis,
  AdminKpisQuery,
  AdminShift,
  PendingTeacher,
  PriorityUser,
  CheckInRequest,
  CheckInResult,
  CongestionSnapshot,
  Core,
  Departure,
  FloorOption,
  ForgotPasswordRequest,
  NoShowStatus,
  NotificationPreferences,
  PriorityRequest,
  PriorityUploadRules,
  RegisterRequest,
  Reservation,
  ReserveRequest,
  ResetPasswordRequest,
  TokenResponse,
  TripPage,
  User,
  WaitFeedbackRequest,
} from '../types/pending';

const BASE_URL = (import.meta.env.VITE_API_URL || '/api').replace(/\/+$/, '');

export class ApiError extends Error {
  readonly status: number;
  /** Errores por campo, si el backend los manda (validación, email o legajo duplicado). */
  readonly violations: FieldViolation[];
  /** Segundos que pide esperar el header `Retry-After` (429). */
  readonly retryAfter?: number;

  constructor(
    status: number,
    message: string,
    violations: FieldViolation[] = [],
    retryAfter?: number,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.violations = violations;
    this.retryAfter = retryAfter;
  }
}

// Datos simulados (#42): prendidos por defecto en `vite dev`, apagados en tests y siempre
// fuera del build de producción (DEV es false ahí y Vite descarta el import).
const USE_MOCKS =
  import.meta.env.DEV &&
  (import.meta.env.VITE_USE_MOCKS ?? String(import.meta.env.MODE === 'development')) === 'true';

// Sesión (#192, ADR 0014): el access token vive solo en memoria; el refresh token (opaco, de un
// solo uso, viaja en el body JSON) en sessionStorage para sobrevivir a recargar la pestaña.
const REFRESH_KEY = 'smartelevate.refresh';
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
    const retry = Number(response.headers.get('Retry-After'));
    throw new ApiError(response.status, message, violations, retry > 0 ? retry : undefined);
  }

  // 204, o 202 sin cuerpo (p. ej. /auth/password/forgot): no hay JSON que leer.
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

// Con el storage bloqueado (modo privado estricto) la sesión sigue, pero no sobrevive a recargar.
function storage<T>(action: () => T): T | null {
  try {
    return action();
  } catch {
    return null;
  }
}
const readRefresh = () => storage(() => sessionStorage.getItem(REFRESH_KEY));

function saveTokens({ accessToken: access, refreshToken }: TokenResponse) {
  accessToken = access;
  storage(() => sessionStorage.setItem(REFRESH_KEY, refreshToken));
}

function clearTokens() {
  accessToken = null;
  storage(() => sessionStorage.removeItem(REFRESH_KEY));
}

/**
 * Canjea el refresh token por un par nuevo (rotación). Las llamadas simultáneas comparten el
 * intento: el refresh es de un solo uso y un segundo canje revocaría toda la sesión.
 */
function refreshAccessToken(): Promise<boolean> {
  const refreshToken = readRefresh();
  if (!refreshToken) return Promise.resolve(false);
  refreshing ??= send<TokenResponse>('/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken } satisfies RefreshRequest),
  })
    .then(
      (tokens) => {
        saveTokens(tokens);
        return true;
      },
      () => {
        clearTokens();
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

/** Confirma el email con el token del link. 400 si no existe, ya se usó o venció. */
export const verifyEmail = (body: VerifyEmailRequest) => api.post<void>('/auth/verify', body);
/** Reenvía el link de verificación. 202 exista o no la cuenta; 429 trae `retryAfter`. */
export const resendVerification = (body: ResendVerificationRequest) =>
  api.post<void>('/auth/resend-verification', body);

/** Inicia sesión y devuelve el usuario según `/me` (rol y prioridad los decide el servidor). */
export async function login(body: LoginRequest): Promise<User> {
  saveTokens(await api.post<TokenResponse>('/auth/login', body));
  return getMe();
}

/** Recupera la sesión al abrir la app con el refresh token guardado. `null` si no hay sesión. */
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
  const refreshToken = readRefresh();
  try {
    if (refreshToken) {
      await api.post<void>('/auth/logout', { refreshToken } satisfies RefreshRequest);
    }
  } catch {
    // Aunque el backend no responda, la sesión local se cierra igual.
  } finally {
    clearTokens();
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

/** Docentes declarados pendientes de aprobación. Solo ADMIN. */
export const getPendingTeachers = () => api.get<PendingTeacher[]>('/admin/teachers/pending');
export const approveTeacher = (id: string) =>
  api.post<void>(`/admin/teachers/${encodeURIComponent(id)}/approve`, {});
export const rejectTeacher = (id: string) =>
  api.post<void>(`/admin/teachers/${encodeURIComponent(id)}/reject`, {});
/** Usuarios con prioridad vigente. */
export const getPriorityUsers = () => api.get<PriorityUser[]>('/admin/priority-users');
export const revokePriority = (id: string) =>
  api.delete<void>(`/admin/priority-users/${encodeURIComponent(id)}`);
