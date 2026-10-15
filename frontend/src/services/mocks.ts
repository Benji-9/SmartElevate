// Datos simulados para desarrollar sin backend (#42). Solo se cargan en `vite dev`
// con VITE_USE_MOCKS (ver api.ts); nunca entran al build de producción.
// Para apagar un endpoint cuando el real esté listo, borrá su ruta de `routes`:
// lo que no matchea acá sigue de largo al backend.
// Edificios, núcleos y pisos del relevamiento (docs/reglas/asignacion.md, #22); congestión,
// halls y ocupación siguen siendo de ejemplo.
import type {
  Building,
  CheckIn,
  CheckInRequest,
  CreatedKiosk,
  CreateKioskRequest,
  Elevator,
  Kiosk,
  KioskList,
  LoginRequest,
  NoShowStatus,
  PendingTeacher,
  PriorityUser,
  QrToken,
  RefreshRequest,
  RegisterRequest,
  Reservation,
  ReservationPage,
  ReservationStatus,
  ResetPasswordRequest,
  TokenResponse,
  User,
} from '../types/api';
import { ApiError } from './api';
import type {
  AdminKpis,
  AdminPeriod,
  AdminShift,
  CongestionSnapshot,
  Core,
  Departure,
  FloorOption,
  NotificationPreferences,
  PriorityRequest,
  PriorityUploadRules,
  ReserveRequest,
} from '../types/pending';

// Parámetros "de la API": las pantallas los leen de las respuestas, no de acá.
const DEPARTURE_MINUTES = 2;
const CAPACITY = 10;
const WINDOW_MINUTES = 30;
const CONGESTION_REFRESH_SECONDS = 60;
/** Pisos bajos (`low_floors_threshold`): sin reserva con destino del 1 a este piso. */
const LOW_FLOORS_THRESHOLD = 4;
/** Se puede cancelar sin falta hasta este tiempo antes de la salida. */
const CANCEL_DEADLINE_MS = 60_000;
const UPLOAD_RULES: PriorityUploadRules = {
  maxSizeBytes: 5 * 1024 * 1024,
  acceptedTypes: ['application/pdf', 'image/jpeg', 'image/png'],
};
const NO_SHOW_RULE = { threshold: 3, windowDays: 7, suspensionHours: 24 };
const TRIPS_PAGE_SIZE = 10;
const DAY_MS = 86_400_000;
const BA_OFFSET_MS = 3 * 3_600_000;
/** `app.kiosk.qr-ttl` del backend (ADR 0020). */
const QR_TTL_MS = 30_000;
// Kioscos del QR (#229): ascensores de ejemplo de Independencia 2 y Lima 3.
const ELEVATORS: Elevator[] = [
  {
    code: '33',
    building: 'INDEPENDENCIA',
    core: 'IND2',
    coreName: 'Independencia 2',
    floors: [0, 5, 6, 7, 8, 9, 10, 11],
    usage: 'COMMON',
    active: true,
  },
  {
    code: '34',
    building: 'INDEPENDENCIA',
    core: 'IND2',
    coreName: 'Independencia 2',
    floors: [0, 5, 6, 7, 8, 9, 10, 11],
    usage: 'COMMON',
    active: true,
  },
  {
    code: '12',
    building: 'LIMA',
    core: 'L3',
    coreName: 'Lima 3',
    floors: [0, 7, 8, 9, 10],
    usage: 'COMMON',
    active: true,
  },
];
let kiosks: Kiosk[] = [];
// Cola de docentes y prioritarios del panel admin (#140). Ids UUID y mensajes de error como los
// del backend (PriorityService): 404 PriorityNotFoundException, 409 InvalidPriorityOperation.
let pendingTeachers: PendingTeacher[] = [
  {
    id: '0b9f1c2e-4d1a-4c3e-9a51-1f2e3d4c5b01',
    fullName: 'Marta Gómez',
    email: 'marta.gomez@uade.edu.ar',
    legajo: '2001234',
    registeredAt: '2026-10-08T14:20:00Z',
  },
  {
    id: '0b9f1c2e-4d1a-4c3e-9a51-1f2e3d4c5b02',
    fullName: 'Julián Ruiz',
    email: 'julian.ruiz@uade.edu.ar',
    legajo: '2005678',
    registeredAt: '2026-10-11T09:05:00Z',
  },
];
let priorityUsers: PriorityUser[] = [
  {
    id: '0b9f1c2e-4d1a-4c3e-9a51-1f2e3d4c5b03',
    fullName: 'Carla Díaz',
    email: 'carla.diaz@uade.edu.ar',
    legajo: '1088888',
    category: 'REDUCED_MOBILITY',
  },
  {
    id: '0b9f1c2e-4d1a-4c3e-9a51-1f2e3d4c5b04',
    fullName: 'Rubén Sosa',
    email: 'ruben.sosa@uade.edu.ar',
    legajo: '2000456',
    category: 'TEACHER',
  },
];

const PERIOD_DAYS: Record<AdminPeriod, number> = { TODAY: 1, WEEK: 7, MONTH: 30 };
// Turnos de cursada (kpis.md#filtros): el turno mañana cubre las cursadas 7:45–11:45 y 8:15–12:15.
const SHIFTS: AdminShift[] = [
  { id: 'MORNING', name: 'Turno mañana', startsAt: '07:00', endsAt: '12:15' },
];
const minutesOf = (hhmm: string) => {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + m;
};
// Reservas de un día típico por hora (7 a 22 h), con picos en los cambios de clase.
const FIRST_HOUR = 7;
const HOURLY_RESERVATIONS = [28, 18, 42, 25, 12, 30, 22, 15, 20, 38, 16, 10, 34, 26, 12, 6];
// Parte de las reservas, ocupación y espera promedio de cada núcleo (panel admin).
const CORE_STATS: Record<string, { share: number; occupancyPercent: number; waitSeconds: number }> =
  {
    L1: { share: 0.32, occupancyPercent: 92, waitSeconds: 420 },
    L2: { share: 0.26, occupancyPercent: 70, waitSeconds: 260 },
    L3: { share: 0.14, occupancyPercent: 41, waitSeconds: 90 },
    // Solo ascensores dedicados (docentes y movilidad reducida): pocas reservas.
    IND1: { share: 0.04, occupancyPercent: 35, waitSeconds: 60 },
    IND2: { share: 0.24, occupancyPercent: 58, waitSeconds: 180 },
  };

// Usuarios de ejemplo: cualquier contraseña sirve, salvo "incorrecta".
const users: User[] = [
  {
    id: 'u-1',
    email: 'ana.perez@uade.edu.ar',
    fullName: 'Ana Pérez',
    legajo: '1099999',
    role: 'USER',
    priority: 'NONE',
    declaredUserType: 'STUDENT',
  },
  {
    id: 'u-2',
    email: 'admin@uade.edu.ar',
    fullName: 'Admin UADE',
    legajo: '1000001',
    role: 'ADMIN',
    priority: 'NONE',
    declaredUserType: 'STUDENT',
  },
  {
    id: 'u-3',
    email: 'sin.verificar@uade.edu.ar',
    fullName: 'Sin Verificar',
    legajo: '1000002',
    role: 'USER',
    priority: 'NONE',
    declaredUserType: 'STUDENT',
  },
];

// Cuentas que todavía no hicieron click en el link de verificación.
const unverified = new Set(['sin.verificar@uade.edu.ar']);

// Simula la sesión del servidor (refresh token vigente): sobrevive a recargar la página.
const SESSION_KEY = 'smartelevate-mock-session';

const REFRESH_KEY = 'smartelevate-mock-refresh';
let tokenCounter = 0;

function tokensFor(email: string): TokenResponse {
  const refreshToken = `mock-refresh-${++tokenCounter}`;
  sessionStorage.setItem(SESSION_KEY, email);
  sessionStorage.setItem(REFRESH_KEY, refreshToken);
  return { accessToken: `mock-${Date.now()}`, refreshToken, tokenType: 'Bearer', expiresIn: 900 };
}

function currentUser(): User {
  const email = sessionStorage.getItem(SESSION_KEY);
  const found = users.find((u) => u.email === email);
  if (!found) throw new ApiError(401, 'Sesión vencida');
  return found;
}

const range = (from: number, to: number) =>
  Array.from({ length: to - from + 1 }, (_, i) => from + i);

const cores: Core[] = [
  {
    id: 'L1',
    buildingId: 'LIMA',
    name: 'Lima 1',
    floors: range(-3, 7),
    congestion: 'HIGH',
    estimatedWaitMinutes: 9,
    hall: 'Hall Lima, planta baja',
  },
  {
    id: 'L2',
    buildingId: 'LIMA',
    name: 'Lima 2',
    // Unión de las dos baterías (07–08: −4 a 5; 35–36: −2, 0, 2 a 10) hasta que el
    // contrato las exponga (#101).
    floors: range(-4, 10),
    congestion: 'MEDIUM',
    estimatedWaitMinutes: 5,
    hall: 'Hall Lima, planta baja',
  },
  {
    id: 'L3',
    buildingId: 'LIMA',
    name: 'Lima 3',
    floors: range(-3, 10),
    congestion: 'LOW',
    estimatedWaitMinutes: 2,
    hall: 'Hall Lima, entrepiso',
  },
  {
    id: 'IND1',
    buildingId: 'INDEPENDENCIA',
    name: 'Independencia 1',
    // Ascensores dedicados (docentes y movilidad reducida, #101).
    floors: range(-4, 10),
    congestion: 'LOW',
    estimatedWaitMinutes: 1,
    hall: 'Hall Independencia, planta baja',
  },
  {
    id: 'IND2',
    buildingId: 'INDEPENDENCIA',
    name: 'Independencia 2',
    // No para en el 1.
    floors: [...range(-4, 0), ...range(2, 11)],
    congestion: 'LOW',
    estimatedWaitMinutes: 3,
    hall: 'Hall Independencia, planta baja',
  },
];

// Ocupación de ejemplo por salida; la segunda está llena para probar el 409.
const OCCUPANCY = [4, 10, 7, 2, 9, 0, 5, 3, 8, 1, 6, 2, 0, 4, 1];

// Misma forma que GET /api/buildings: los núcleos van con código y nombre visible.
const coresOf = (buildingCode: string) =>
  cores.filter((c) => c.buildingId === buildingCode).map((c) => ({ code: c.id, name: c.name }));

const buildings: Building[] = [
  { code: 'LIMA', name: 'Lima', minFloor: -4, maxFloor: 10, cores: coresOf('LIMA') },
  {
    code: 'INDEPENDENCIA',
    name: 'Independencia',
    minFloor: -4,
    maxFloor: 11,
    cores: coresOf('INDEPENDENCIA'),
  },
];

function departuresFor(coreId: string): Departure[] {
  const step = DEPARTURE_MINUTES * 60_000;
  const first = Math.ceil(Date.now() / step) * step + step;
  return Array.from({ length: WINDOW_MINUTES / DEPARTURE_MINUTES }, (_, i) => {
    const departsAt = new Date(first + i * step).toISOString();
    return {
      id: `${coreId}-${departsAt}`,
      coreId,
      departsAt,
      durationMinutes: DEPARTURE_MINUTES,
      capacity: CAPACITY,
      occupied: OCCUPANCY[i % OCCUPANCY.length],
    };
  });
}

function findDeparture(id: string): Departure {
  const departure = cores.flatMap((c) => departuresFor(c.id)).find((d) => d.id === id);
  if (!departure) throw new ApiError(404, 'La salida no existe o ya cerró');
  return departure;
}

/** Las salidas de los mocks son por núcleo, no por ascensor: el turno siempre es del 33. */
const RESERVED_ELEVATOR = '33';

/** Misma forma que `ReservationResponse` del backend. */
function newReservation(
  id: number,
  core: Core,
  departureAt: string,
  originFloor: number,
  destinationFloor: number,
  status: ReservationStatus = 'ACTIVE',
  checkInOutcome: Reservation['checkInOutcome'] = null,
): Reservation {
  return {
    id,
    departureId: 1000 + id,
    elevator: RESERVED_ELEVATOR,
    coreName: core.name,
    buildingName: buildings.find((b) => b.code === core.buildingId)!.name,
    departureAt,
    seatNumber: status.startsWith('CANCELLED') ? null : 3,
    originFloor,
    destinationFloor,
    status,
    createdAt: new Date(Date.parse(departureAt) - 10 * 60_000).toISOString(),
    cancelCountsAsNoShow: false,
    checkInOutcome,
  };
}

/** El servidor calcula si cancelar ya cuenta como falta; acá se recalcula en cada lectura. */
const withCancelRule = (r: Reservation): Reservation => ({
  ...r,
  cancelCountsAsNoShow:
    r.status === 'ACTIVE' && Date.parse(r.departureAt) - Date.now() < CANCEL_DEADLINE_MS,
});

const coreOf = (id: string) => cores.find((c) => c.id === id)!;

// Estado en memoria: se reinicia al recargar la página.
let activeReservation: Reservation | null = newReservation(
  1,
  coreOf('L2'),
  departuresFor('L2')[3].departsAt,
  0,
  7,
);
/** Turno del último check-in, para "ya escaneado". */
let lastCheckedIn: number | null = null;
let checkInCount = 0;
let priorityRequest: PriorityRequest | null = null;
let notificationPreferences: NotificationPreferences = {
  departureReminder: false,
  spotReleased: false,
  delayCancellation: false,
  priorityAccess: false,
};

// Historial de ejemplo: dos viajes por día hacia atrás, con todos los estados y resultados.
const TRIP_RESULTS: [ReservationStatus, Reservation['checkInOutcome']][] = [
  ['USED', 'ON_TIME'],
  ['USED', 'ON_TIME'],
  ['USED', 'OTHER_ELEVATOR'],
  ['NO_SHOW', 'LATE'],
  ['CANCELLED', null],
  ['NO_SHOW', null],
  ['CANCELLED_LATE', null],
];
const trips: Reservation[] = Array.from({ length: 24 }, (_, i) => {
  const day = Math.floor(i / 2) + 1;
  const departsAt = new Date(Date.now() - day * 86_400_000 - (i % 2) * 3 * 3_600_000);
  departsAt.setUTCSeconds(0, 0);
  const [status, outcome] = TRIP_RESULTS[i % TRIP_RESULTS.length];
  // 5 a 7: fuera de los pisos bajos y servidos por todos los núcleos.
  return newReservation(
    100 - i,
    cores[i % cores.length],
    departsAt.toISOString(),
    0,
    (i % 3) + 5,
    status,
    outcome,
  );
});

/**
 * Tokens de prueba del check-in: los del kiosco simulado (`mock-qr-<ascensor>-…`) traen su
 * ascensor; "VENCIDO" es inválido, "OTRO" da el ascensor 34 y "TARDE" fuera de hora. Cualquier
 * otro token no vacío (p. ej. el "DEMO" del modo demo) es del ascensor del turno.
 */
function scanned(token: string) {
  const normalized = token.trim().toUpperCase();
  if (!normalized || normalized.includes('VENCIDO')) {
    throw new ApiError(
      400,
      'El QR no es válido o ya venció. Escaneá el que está en pantalla.',
      [],
      undefined,
      'INVALID_QR_TOKEN',
    );
  }
  const elevator =
    /^mock-qr-([^-]+)-/.exec(token)?.[1] ??
    (normalized.includes('OTRO') ? '34' : RESERVED_ELEVATOR);
  return { elevator, late: normalized.includes('TARDE') };
}

type Handler = (
  body: unknown,
  params: string[],
  query: URLSearchParams,
  headers: Headers,
) => unknown;

const routes: [method: string, path: RegExp, handler: Handler][] = [
  [
    'POST',
    /^\/auth\/login$/,
    (body) => {
      const { email, password } = body as LoginRequest;
      const found = users.find((u) => u.email === email.trim().toLowerCase());
      if (!found || password === 'incorrecta') {
        throw new ApiError(401, 'Email o contraseña incorrectos');
      }
      if (unverified.has(found.email)) {
        throw new ApiError(403, 'Tu cuenta todavía no está verificada. Revisá tu email.');
      }
      return tokensFor(found.email);
    },
  ],
  [
    'POST',
    /^\/auth\/refresh$/,
    (body) => {
      const { refreshToken } = body as RefreshRequest;
      const email = sessionStorage.getItem(SESSION_KEY);
      // Un solo uso, como el real: el token viejo ya no sirve.
      if (!email || refreshToken !== sessionStorage.getItem(REFRESH_KEY)) {
        sessionStorage.removeItem(SESSION_KEY);
        throw new ApiError(401, 'Sesión vencida');
      }
      return tokensFor(email);
    },
  ],
  ['POST', /^\/auth\/resend-verification$/, () => undefined],
  [
    'POST',
    /^\/auth\/logout$/,
    () => {
      sessionStorage.removeItem(SESSION_KEY);
      sessionStorage.removeItem(REFRESH_KEY);
    },
  ],
  [
    'POST',
    /^\/auth\/register$/,
    (body) => {
      const { fullName, email, legajo, declaredUserType } = body as RegisterRequest;
      const normalized = email.trim().toLowerCase();
      const violations = [
        users.some((u) => u.email === normalized) && {
          field: 'email',
          message: 'Ya hay una cuenta con ese email.',
        },
        users.some((u) => u.legajo === legajo) && {
          field: 'legajo',
          message: 'Ya hay una cuenta con ese legajo.',
        },
      ].filter((v) => v !== false);
      if (violations.length) throw new ApiError(409, 'La cuenta ya existe', violations);
      // Nace como usuario común y sin verificar: el tipo declarado no da rol ni prioridad.
      users.push({
        id: `u-${Date.now()}`,
        email: normalized,
        fullName,
        legajo,
        role: 'USER',
        priority: 'NONE',
        declaredUserType,
      });
      unverified.add(normalized);
    },
  ],
  [
    'POST',
    /^\/auth\/verify$/,
    (body) => {
      // Tokens de prueba: "VENCIDO" y "USADO" dan el 400 con su motivo.
      const token = ((body as { token?: string }).token ?? '').toUpperCase();
      if (token.includes('VENCIDO')) throw new ApiError(400, 'El link venció. Pedí uno nuevo.');
      if (token.includes('USADO')) throw new ApiError(400, 'Este link ya se usó.');
      if (!token) throw new ApiError(400, 'El link no es válido.');
      return undefined;
    },
  ],
  [
    'POST',
    /^\/auth\/resend-verification$/,
    (body) => {
      // "limite@uade.edu.ar" simula el 429 con Retry-After de 30 s.
      if ((body as { email: string }).email.startsWith('limite@')) {
        throw new ApiError(429, 'Ya te mandamos un email hace poco.', [], 30);
      }
      return undefined; // 202 exista o no la cuenta
    },
  ],
  [
    'POST',
    /^\/auth\/password\/forgot$/,
    // 202 exista o no la cuenta, para no revelar qué emails están registrados.
    () => undefined,
  ],
  [
    'POST',
    /^\/auth\/password\/reset$/,
    (body) => {
      // Token de prueba "VENCIDO": el link ya no sirve.
      const { token, newPassword } = body as ResetPasswordRequest;
      if (!token || token.toUpperCase().includes('VENCIDO')) {
        throw new ApiError(400, 'El link venció o ya se usó. Pedí uno nuevo.');
      }
      if (!newPassword) {
        throw new ApiError(400, 'Datos inválidos', [
          { field: 'newPassword', message: 'Elegí una contraseña.' },
        ]);
      }
    },
  ],
  ['GET', /^\/users\/me$/, () => currentUser()],
  ['GET', /^\/buildings$/, () => buildings],
  ['GET', /^\/cores$/, () => cores],
  ['GET', /^\/cores\/([^/]+)\/departures$/, (_, [coreId]) => departuresFor(coreId)],
  [
    'GET',
    /^\/congestion$/,
    () =>
      ({
        updatedAt: new Date(Date.now() - 2 * 60_000).toISOString(),
        refreshAfterSeconds: CONGESTION_REFRESH_SECONDS,
        cores: cores.map((c) => ({
          coreId: c.id,
          name: c.name,
          level: c.congestion,
          estimatedWaitMinutes: c.estimatedWaitMinutes,
        })),
      }) satisfies CongestionSnapshot,
  ],
  [
    'GET',
    /^\/cores\/([^/]+)\/floors$/,
    (_, [coreId], query) => {
      const core = cores.find((c) => c.id === coreId);
      if (!core) throw new ApiError(404, 'El núcleo no existe');
      const origin = Number(query.get('origin'));
      const exempt = currentUser().priority === 'REDUCED_MOBILITY';
      return core.floors.map((floor): FloorOption => {
        if (floor === origin) return { floor, eligible: false, reason: 'Es tu piso de origen.' };
        if (!exempt && floor >= 1 && floor <= LOW_FLOORS_THRESHOLD) {
          return {
            floor,
            eligible: false,
            reason: `Hasta el piso ${LOW_FLOORS_THRESHOLD} usá la escalera.`,
          };
        }
        return { floor, eligible: true, reason: null };
      });
    },
  ],
  [
    'GET',
    /^\/reservations\/history$/,
    (_, __, query) => {
      const start = Number(query.get('cursor') ?? 0);
      const end = start + TRIPS_PAGE_SIZE;
      return {
        items: trips.slice(start, end),
        nextCursor: end < trips.length ? String(end) : null,
      } satisfies ReservationPage;
    },
  ],
  ['GET', /^\/reservations\/active$/, () => activeReservation && withCancelRule(activeReservation)],
  [
    'GET',
    /^\/reservations\/([^/]+)$/,
    (_, [id]) => {
      const found = [activeReservation, ...trips].find((r) => String(r?.id) === id);
      if (!found) throw new ApiError(404, 'No encontramos ese turno');
      return withCancelRule(found);
    },
  ],
  [
    'POST',
    /^\/reservations$/,
    (body) => {
      const { departureId, originFloor, destinationFloor } = body as ReserveRequest;
      if (activeReservation) {
        throw new ApiError(409, 'Ya tenés un turno activo. Cancelalo para reservar otro.');
      }
      const departure = findDeparture(departureId);
      if (departure.occupied >= departure.capacity) {
        throw new ApiError(409, 'La salida está llena. Elegí otra.');
      }
      activeReservation = newReservation(
        Date.now(),
        coreOf(departure.coreId),
        departure.departsAt,
        originFloor,
        destinationFloor,
      );
      return withCancelRule(activeReservation);
    },
  ],
  [
    'DELETE',
    /^\/reservations\/([^/]+)$/,
    (_, [id]) => {
      if (String(activeReservation?.id) !== id) throw new ApiError(404, 'No encontramos ese turno');
      activeReservation = null;
    },
  ],
  [
    'POST',
    /^\/check-ins$/,
    (body) => {
      const { elevator, late } = scanned((body as CheckInRequest).token);
      if (!activeReservation) {
        throw lastCheckedIn
          ? new ApiError(
              409,
              'Ya registraste el check-in de este turno.',
              [],
              undefined,
              'ALREADY_CHECKED_IN',
            )
          : new ApiError(
              409,
              'No tenés un turno para hacer check-in.',
              [],
              undefined,
              'NO_ACTIVE_RESERVATION',
            );
      }
      const outcome = late ? 'LATE' : elevator === RESERVED_ELEVATOR ? 'ON_TIME' : 'OTHER_ELEVATOR';
      const result: CheckIn = {
        reservationId: ++checkInCount,
        outcome,
        reservationStatus: late ? 'NO_SHOW' : 'USED',
        elevator,
        reservedElevator: RESERVED_ELEVATOR,
        departureAt: activeReservation.departureAt,
        checkedInAt: new Date().toISOString(),
        source: 'SIMULATED',
      };
      lastCheckedIn = activeReservation.id;
      activeReservation = null;
      return result;
    },
  ],
  ['GET', /^\/priority-requests\/me$/, () => priorityRequest],
  ['GET', /^\/priority-requests\/upload-rules$/, () => UPLOAD_RULES],
  [
    'POST',
    /^\/priority-requests$/,
    (body) => {
      const form = body as FormData;
      const file = form.get('certificate');
      if (form.get('consentAccepted') !== 'true') {
        throw new ApiError(400, 'Tenés que aceptar el tratamiento del certificado.');
      }
      if (!(file instanceof File)) throw new ApiError(400, 'Adjuntá el certificado.');
      // El backend real valida por contenido (magic bytes); el mock, por el tipo declarado.
      if (!UPLOAD_RULES.acceptedTypes.includes(file.type)) {
        throw new ApiError(415, 'El archivo tiene que ser PDF, JPG o PNG.');
      }
      if (file.size > UPLOAD_RULES.maxSizeBytes) {
        throw new ApiError(413, 'El archivo supera el tamaño máximo.');
      }
      if (priorityRequest?.status === 'PENDING') {
        throw new ApiError(409, 'Ya tenés una solicitud pendiente.');
      }
      priorityRequest = {
        category: 'REDUCED_MOBILITY',
        status: 'PENDING',
        submittedAt: new Date().toISOString(),
        expiresAt: null,
      };
      return priorityRequest;
    },
  ],
  [
    'GET',
    /^\/me\/no-shows$/,
    () => {
      const since = Date.now() - NO_SHOW_RULE.windowDays * 86_400_000;
      return {
        ...NO_SHOW_RULE,
        // Falta = NO_SHOW (con o sin check-in fuera de hora) o CANCELLED_LATE (turnos.md §5).
        recentNoShows: trips.filter(
          (t) =>
            (t.status === 'NO_SHOW' || t.status === 'CANCELLED_LATE') &&
            Date.parse(t.departureAt) >= since,
        ).length,
        suspendedUntil: null,
        exempt: currentUser().priority !== 'NONE',
      } satisfies NoShowStatus;
    },
  ],
  ['GET', /^\/me\/notification-preferences$/, () => notificationPreferences],
  [
    'PUT',
    /^\/me\/notification-preferences$/,
    (body) => (notificationPreferences = body as NotificationPreferences),
  ],
  ['GET', /^\/admin\/shifts$/, () => SHIFTS],
  ['GET', /^\/admin\/teachers\/pending$/, () => pendingTeachers],
  [
    'POST',
    /^\/admin\/teachers\/([^/]+)\/(approve|reject)$/,
    (_, [id, action]) => {
      const teacher = pendingTeachers.find((t) => t.id === id);
      if (!teacher) throw new ApiError(404, 'Usuario inexistente');
      pendingTeachers = pendingTeachers.filter((t) => t.id !== id);
      if (action === 'approve') {
        const { fullName, email, legajo } = teacher;
        priorityUsers = [...priorityUsers, { id, fullName, email, legajo, category: 'TEACHER' }];
      }
      return undefined;
    },
  ],
  ['GET', /^\/elevators$/, () => ELEVATORS],
  ['GET', /^\/admin\/kiosks$/, () => ({ simulatorEnabled: true, kiosks }) satisfies KioskList],
  [
    'POST',
    /^\/admin\/kiosks$/,
    (body) => {
      const { elevatorId, simulated = false } = body as CreateKioskRequest;
      if (!ELEVATORS.some((e) => e.code === elevatorId)) {
        throw new ApiError(404, 'El ascensor no existe');
      }
      const kiosk = { id: crypto.randomUUID(), elevatorId, simulated };
      const created = {
        ...kiosk,
        createdAt: new Date().toISOString(),
        secret: `mock-${elevatorId}-${kiosk.id}`,
      };
      kiosks = [...kiosks, { ...kiosk, createdAt: created.createdAt, lastSeenAt: null }];
      return created satisfies CreatedKiosk;
    },
  ],
  [
    'POST',
    /^\/admin\/kiosks\/([^/]+)\/revoke$/,
    (_, [id]) => {
      if (!kiosks.some((k) => k.id === id)) throw new ApiError(404, 'El kiosco no existe');
      kiosks = kiosks.map((k) => (k.id === id ? { ...k, revokedAt: new Date().toISOString() } : k));
      return undefined;
    },
  ],
  // El ascensor sale de la credencial: sirve cualquier `mock-<ascensor>-…` (como las del alta).
  // Ciclos de 30 s alineados al reloj.
  [
    'GET',
    /^\/kiosks\/me\/qr-token$/,
    (_body, _params, _query, headers) => {
      const elevatorId = /^Bearer mock-([^-]+)-/.exec(headers.get('Authorization') ?? '')?.[1];
      if (!elevatorId) {
        throw new ApiError(
          401,
          'Credencial de kiosco inválida',
          [],
          undefined,
          'KIOSK_UNAUTHORIZED',
        );
      }
      const expiresAt = Math.floor(Date.now() / QR_TTL_MS + 1) * QR_TTL_MS;
      return {
        token: `mock-qr-${elevatorId}-${expiresAt}`,
        expiresAt: new Date(expiresAt).toISOString(),
        elevatorId,
      } satisfies QrToken;
    },
  ],
  ['GET', /^\/admin\/priority-users$/, () => priorityUsers],
  [
    'DELETE',
    /^\/admin\/priority-users\/([^/]+)$/,
    (_, [id]) => {
      if (!priorityUsers.some((u) => u.id === id)) {
        throw new ApiError(404, 'El usuario no tiene una prioridad vigente');
      }
      priorityUsers = priorityUsers.filter((u) => u.id !== id);
      return undefined;
    },
  ],
  [
    'GET',
    /^\/admin\/kpis$/,
    (_, __, query) => {
      const days = PERIOD_DAYS[query.get('period') as AdminPeriod];
      if (!days) throw new ApiError(400, 'Período inválido');
      const buildingId = query.get('buildingId');
      const selected = cores.filter((c) => !buildingId || c.buildingId === buildingId);
      if (!selected.length) throw new ApiError(404, 'La sede no existe');
      const shiftId = query.get('shiftId');
      const shift = SHIFTS.find((s) => s.id === shiftId);
      if (shiftId && !shift) throw new ApiError(404, 'El turno no existe');
      // Horas que se solapan con el turno (con 12:15 entra la hora de las 12).
      const inShift = (hour: number) =>
        !shift ||
        (hour * 60 < minutesOf(shift.endsAt) && (hour + 1) * 60 > minutesOf(shift.startsAt));

      const share = selected.reduce((sum, c) => sum + CORE_STATS[c.id].share, 0);
      const reservationsByHour = HOURLY_RESERVATIONS.map((n, i) => ({
        hour: FIRST_HOUR + i,
        reservations: Math.round(n * days * share),
      })).filter((h) => inShift(h.hour));
      const reservations = reservationsByHour.reduce((sum, h) => sum + h.reservations, 0);
      const checkIns = Math.round(reservations * 0.81);
      // ponytail: Buenos Aires fijo en UTC-3 (hoy no tiene horario de verano).
      const to = Math.floor((Date.now() - BA_OFFSET_MS) / DAY_MS + 1) * DAY_MS + BA_OFFSET_MS;
      return {
        from: new Date(to - days * DAY_MS).toISOString(),
        to: new Date(to).toISOString(),
        avgWaitSeconds: Math.round(
          selected.reduce((sum, c) => sum + CORE_STATS[c.id].waitSeconds, 0) / selected.length,
        ),
        wait5To10Percent: 18,
        baselinePercent: 43.5,
        reservations,
        checkIns,
        checkInPercent: reservations ? Math.round((checkIns / reservations) * 100) : 0,
        avgOccupancy: 6.8,
        capacity: CAPACITY,
        reservationsByHour,
        cores: selected.map((c) => ({
          coreId: c.id,
          name: c.name,
          reservations: Math.round((reservations * CORE_STATS[c.id].share) / share),
          occupancyPercent: CORE_STATS[c.id].occupancyPercent,
          avgWaitSeconds: CORE_STATS[c.id].waitSeconds,
          congestion: c.congestion,
        })),
      } satisfies AdminKpis;
    },
  ],
];

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Devuelve `{ data }` si hay un mock para la ruta, o `undefined` para que la llamada
 * siga al backend real. Los errores salen como `ApiError`, igual que desde `fetch`.
 */
export async function mockRequest(
  method: string,
  path: string,
  body?: BodyInit | null,
  latencyMs = 300 + Math.random() * 500,
  headers?: HeadersInit,
): Promise<{ data: unknown } | undefined> {
  const [pathname, search] = path.split('?');
  for (const [routeMethod, pattern, handler] of routes) {
    const match = routeMethod === method ? pattern.exec(pathname) : null;
    if (!match) continue;
    await delay(latencyMs);
    // JSON como string; FormData (subida de archivos) pasa tal cual.
    const parsed: unknown = typeof body === 'string' ? JSON.parse(body) : (body ?? undefined);
    const params = match.slice(1).map(decodeURIComponent);
    return { data: handler(parsed, params, new URLSearchParams(search), new Headers(headers)) };
  }
  return undefined;
}
