import type {
  AdminElevator,
  AdminKpis,
  AdminKpisQuery,
  AdminShift,
  ApiErrorBody,
  Building,
  CongestionSnapshot,
  CheckIn,
  CheckInRequest,
  CreatedKiosk,
  CreateKioskRequest,
  Departure,
  Elevator,
  ElevatorServiceResult,
  FieldViolation,
  KioskList,
  ForgotPasswordRequest,
  LoginRequest,
  NoShowStatus,
  NotificationPreferences,
  PendingTeacher,
  PingResponse,
  PriorityUser,
  AdminUserPage,
  Role,
  QrToken,
  RegisterRequest,
  Reservation,
  ReservationPage,
  ReserveRequest,
  ResendVerificationRequest,
  ResetPasswordRequest,
  TokenResponse,
  User,
  VerifyEmailRequest,
} from '../types/api';

const BASE_URL = (import.meta.env.VITE_API_URL || '/api').replace(/\/+$/, '');

export class ApiError extends Error {
  readonly status: number;
  /** Errores por campo, si el backend los manda (validación, email o legajo duplicado). */
  readonly violations: FieldViolation[];
  /** Segundos que pide esperar el header `Retry-After` (429). */
  readonly retryAfter?: number;
  /** Código estable de los errores de dominio (`INVALID_QR_TOKEN`, `ALREADY_CHECKED_IN`…). */
  readonly code?: string;

  constructor(
    status: number,
    message: string,
    violations: FieldViolation[] = [],
    retryAfter?: number,
    code?: string,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.violations = violations;
    this.retryAfter = retryAfter;
    this.code = code;
  }
}

// Datos simulados (#42): prendidos por defecto en `vite dev`, apagados en tests y siempre
// fuera del build de producción (DEV es false ahí y Vite descarta el import).
const USE_MOCKS =
  import.meta.env.DEV &&
  (import.meta.env.VITE_USE_MOCKS ?? String(import.meta.env.MODE === 'development')) === 'true';

// Sesión (#204, ADR 0019): el access token vive solo en memoria; el refresh token (opaco, de un
// solo uso) viaja en una cookie HttpOnly que el JS no ve y que sobrevive a recargar la pestaña.
// El header custom es la defensa CSRF de /auth/refresh y /auth/logout (ADR 0023).
const COOKIE_AUTH: RequestInit = {
  method: 'POST',
  credentials: 'include',
  headers: { 'X-Requested-With': 'SmartElevate' },
};
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

async function send<T>(
  path: string,
  init?: RequestInit,
  onResponse?: (response: Response) => void,
): Promise<T> {
  if (USE_MOCKS) {
    const { mockRequest } = await import('./mocks');
    const mocked = await mockRequest(
      init?.method ?? 'GET',
      path,
      init?.body,
      undefined,
      init?.headers,
    );
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
  onResponse?.(response);

  if (!response.ok) {
    let message = response.statusText;
    let violations: FieldViolation[] = [];
    let code: string | undefined;
    try {
      const body = (await response.json()) as Partial<ApiErrorBody>;
      message = body.message ?? message;
      violations = body.violations ?? [];
      code = body.code;
    } catch {
      // El cuerpo no es JSON: nos quedamos con statusText.
    }
    const retry = Number(response.headers.get('Retry-After'));
    throw new ApiError(response.status, message, violations, retry > 0 ? retry : undefined, code);
  }

  // 204, o 202 sin cuerpo (p. ej. /auth/password/forgot): no hay JSON que leer.
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

function saveTokens({ accessToken: access }: TokenResponse) {
  accessToken = access;
}

function clearTokens() {
  accessToken = null;
}

/**
 * La cookie del refresh se comparte entre pestañas pero `refreshing` es por pestaña: dos pestañas
 * canjeando a la vez mandarían el mismo token y la segunda se tomaría por reuso (revoca la sesión).
 * El lock del navegador las serializa: la segunda manda la cookie ya rotada (ADR 0023).
 * Sin `navigator.locks` (jsdom, navegadores viejos) se canjea sin lock.
 */
function withRefreshLock<T>(action: () => Promise<T>): Promise<T> {
  return typeof navigator !== 'undefined' && navigator.locks
    ? navigator.locks.request('smartelevate-refresh', action)
    : action();
}

/**
 * Canjea el refresh token por un par nuevo (rotación). Las llamadas simultáneas comparten el
 * intento: el refresh es de un solo uso y un segundo canje revocaría toda la sesión.
 */
function refreshAccessToken(): Promise<boolean> {
  refreshing ??= withRefreshLock(() => send<TokenResponse>('/auth/refresh', COOKIE_AUTH))
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

/** Usuario de la sesión. Rol y prioridad salen de la base, nunca del JWT (ADR 0009). */
export const getMe = () => api.get<User>('/users/me');

/** Crea la cuenta; queda pendiente de verificar por email. */
export const register = (body: RegisterRequest) => api.post<void>('/auth/register', body);

/** Confirma el email con el token del link. 400 si no existe, ya se usó o venció. */
export const verifyEmail = (body: VerifyEmailRequest) => api.post<void>('/auth/verify', body);
/** Reenvía el link de verificación. 202 exista o no la cuenta; 429 trae `retryAfter`. */
export const resendVerification = (body: ResendVerificationRequest) =>
  api.post<void>('/auth/resend-verification', body);

/** Inicia sesión y devuelve el usuario según `/users/me` (rol y prioridad los decide el servidor). */
export async function login(body: LoginRequest): Promise<User> {
  saveTokens(await api.post<TokenResponse>('/auth/login', body, { credentials: 'include' }));
  return getMe();
}

/** Recupera la sesión al abrir la app con la cookie del refresh. `null` si no hay sesión (401). */
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
    await send<void>('/auth/logout', COOKIE_AUTH);
  } catch {
    // Aunque el backend no responda, la sesión local se cierra igual.
  } finally {
    clearTokens();
  }
}
/** Edificios con su rango de pisos y sus núcleos (código y nombre visible). */
export const getBuildings = () => api.get<Building[]>('/buildings');
/**
 * Salidas reservables ahora de un edificio. Ya vienen filtradas para el usuario (solo los
 * ascensores que puede usar) y `remainingSeats` es lo que él puede tomar: el front no decide.
 */
export const getDepartures = (building: string) =>
  api.get<Departure[]>(`/departures?building=${encodeURIComponent(building)}`);
export const getCongestion = () => api.get<CongestionSnapshot>('/congestion');
/** Reserva activa (mientras se le puede hacer check-in); el backend responde 204 si no hay. */
export const getActiveReservation = () =>
  api.get<Reservation | undefined>('/reservations/active').then((r) => r ?? null);
/** Una reserva propia en cualquier estado; 404 `RESERVATION_NOT_FOUND` si no existe o es de otro. */
export const getReservation = (id: string) =>
  api.get<Reservation>(`/reservations/${encodeURIComponent(id)}`);
/**
 * Reserva un lugar. Rechazos por `code`: TURN_FULL (también si lo que queda es de prioritarios),
 * ELEVATOR_NOT_BOOKABLE, OUTSIDE_BOOKING_WINDOW, DEPARTURE_NOT_FOUND, ACTIVE_LIMIT_REACHED,
 * DAILY_LIMIT_REACHED, NO_SHOW_SUSPENDED y los de pisos (SAME_FLOOR, FLOOR_OUT_OF_RANGE,
 * FLOOR_NOT_SERVED, LOW_FLOOR_DESTINATION).
 */
export const reserve = (body: ReserveRequest) => api.post<Reservation>('/reservations', body);
export const cancelReservation = (id: number) => api.delete<void>(`/reservations/${id}`);
/**
 * Check-in con el token leído del QR. Rechazos por `code`: `INVALID_QR_TOKEN` (400),
 * `NO_ACTIVE_RESERVATION`, `OUTSIDE_CHECK_IN_WINDOW` y `ALREADY_CHECKED_IN` (409).
 */
export const checkIn = (body: CheckInRequest) => api.post<CheckIn>('/check-ins', body);
/** Historial de reservas paginado por cursor; sin `cursor` trae la primera página. */
export const getTrips = (cursor?: string | null) =>
  api.get<ReservationPage>(
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
/** Busca usuarios por nombre, email o legajo (mínimo 3 caracteres). Solo ADMIN. */
export const searchUsers = (query: string, cursor?: string) =>
  api.get<AdminUserPage>(
    `/admin/users?query=${encodeURIComponent(query)}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`,
  );
/** Cambia el rol de otro usuario (el servidor rechaza con 409 el propio). Solo ADMIN. */
export const assignRole = (id: string, role: Role) =>
  api.put<void>(`/admin/users/${encodeURIComponent(id)}/role`, { role });
/** Usuarios con prioridad vigente. */
export const getPriorityUsers = () => api.get<PriorityUser[]>('/admin/priority-users');
export const revokePriority = (id: string) =>
  api.delete<void>(`/admin/priority-users/${encodeURIComponent(id)}`);

export const getElevators = () => api.get<Elevator[]>('/elevators');

/** Todos los ascensores con su estado de servicio y las reservas que se cancelarían. Solo ADMIN. */
export const getAdminElevators = () => api.get<AdminElevator[]>('/admin/elevators');
/** Marca un ascensor fuera de servicio: cancela sus reservas sin falta y avisa por mail. Solo ADMIN. */
export const markElevatorOutOfService = (code: string) =>
  api.post<ElevatorServiceResult>(
    `/admin/elevators/${encodeURIComponent(code)}/out-of-service`,
    {},
  );
export const markElevatorInService = (code: string) =>
  api.post<ElevatorServiceResult>(`/admin/elevators/${encodeURIComponent(code)}/in-service`, {});

/** Kioscos del QR (ADR 0020) y si el servidor habilita el simulador. Solo ADMIN. */
export const getKiosks = () => api.get<KioskList>('/admin/kiosks');
/** Da de alta un kiosco. La respuesta trae `secret` por única vez. */
export const createKiosk = (body: CreateKioskRequest) =>
  api.post<CreatedKiosk>('/admin/kiosks', body);
export const revokeKiosk = (id: string) =>
  api.post<void>(`/admin/kiosks/${encodeURIComponent(id)}/revoke`, {});

/**
 * Token del QR con la credencial del kiosco. No usa la sesión: pasa por `send` (sin renovar el
 * JWT ante un 401) y su `Authorization` pisa el de la persona logueada (simulador del admin).
 * `msLeft` se mide contra el header `Date` del servidor, así un reloj de tablet desfasado no
 * corre la rotación. Sin `Date` (CORS, mocks) usa el reloj local.
 */
export async function getKioskQrToken(secret: string) {
  let serverNow = NaN;
  const qr = await send<QrToken>(
    '/kiosks/me/qr-token',
    { method: 'GET', headers: { Authorization: `Bearer ${secret}` } },
    (response) => {
      serverNow = Date.parse(response.headers.get('Date') ?? '');
    },
  );
  const now = Number.isNaN(serverNow) ? Date.now() : serverNow;
  return { ...qr, msLeft: Date.parse(qr.expiresAt) - now };
}
