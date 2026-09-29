// Datos simulados para desarrollar sin backend (#42). Solo se cargan en `vite dev`
// con VITE_USE_MOCKS (ver api.ts); nunca entran al build de producción.
// Para apagar un endpoint cuando el real esté listo, borrá su ruta de `routes`:
// lo que no matchea acá sigue de largo al backend.
// Datos ficticios: los pisos y núcleos reales salen del relevamiento #22.
import { ApiError } from './api';
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
  NoShowStatus,
  NotificationPreferences,
  PriorityRequest,
  PriorityUploadRules,
  RegisterRequest,
  Reservation,
  ReserveRequest,
  Session,
  Trip,
  TripPage,
  TripResult,
  User,
  WaitFeedbackRequest,
} from '../types/pending';

// Parámetros "de la API": las pantallas los leen de las respuestas, no de acá.
const DEPARTURE_MINUTES = 2;
const CAPACITY = 10;
const WINDOW_MINUTES = 30;
const CONGESTION_REFRESH_SECONDS = 60;
/** Regla de pisos bajos (ficticia): trayectos de hasta N pisos van por escalera. */
const LOW_FLOOR_MAX_DISTANCE = 1;
/** Se puede cancelar sin falta hasta este tiempo antes de la salida. */
const CANCEL_DEADLINE_MS = 60_000;
const UPLOAD_RULES: PriorityUploadRules = {
  maxSizeBytes: 5 * 1024 * 1024,
  acceptedTypes: ['application/pdf', 'image/jpeg', 'image/png'],
};
const NO_SHOW_RULE = { threshold: 3, windowDays: 7, suspensionHours: 24 };
const TRIPS_PAGE_SIZE = 10;

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
    declaredUserType: 'STAFF',
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

// Simula la cookie httpOnly de refresh: sobrevive a recargar la página, como la real.
const SESSION_KEY = 'smartelevate-mock-session';

function currentUser(): User {
  const email = sessionStorage.getItem(SESSION_KEY);
  const found = users.find((u) => u.email === email);
  if (!found) throw new ApiError(401, 'Sesión vencida');
  return found;
}

const range = (from: number, to: number) =>
  Array.from({ length: to - from + 1 }, (_, i) => from + i);

const buildings: Building[] = [
  { id: 'LIMA', name: 'Lima', minFloor: -2, maxFloor: 10 },
  { id: 'IND', name: 'Independencia', minFloor: -3, maxFloor: 10 },
];

const cores: Core[] = [
  {
    id: 'L1',
    buildingId: 'LIMA',
    name: 'Lima 1',
    floors: range(-2, 10),
    congestion: 'HIGH',
    estimatedWaitMinutes: 9,
    hall: 'Hall Lima, planta baja',
  },
  {
    id: 'L2',
    buildingId: 'LIMA',
    name: 'Lima 2',
    floors: range(0, 10),
    congestion: 'MEDIUM',
    estimatedWaitMinutes: 5,
    hall: 'Hall Lima, planta baja',
  },
  {
    id: 'L3',
    buildingId: 'LIMA',
    name: 'Lima 3',
    floors: range(0, 6),
    congestion: 'LOW',
    estimatedWaitMinutes: 2,
    hall: 'Hall Lima, entrepiso',
  },
  {
    id: 'IND1',
    buildingId: 'IND',
    name: 'Independencia 1',
    floors: range(-3, 10),
    congestion: 'MEDIUM',
    estimatedWaitMinutes: 6,
    hall: 'Hall Independencia, planta baja',
  },
  {
    id: 'IND2',
    buildingId: 'IND',
    name: 'Independencia 2',
    floors: range(0, 10),
    congestion: 'LOW',
    estimatedWaitMinutes: 3,
    hall: 'Hall Independencia, planta baja',
  },
];

// Ocupación de ejemplo por salida; la segunda está llena para probar el 409.
const OCCUPANCY = [4, 10, 7, 2, 9, 0, 5, 3, 8, 1, 6, 2, 0, 4, 1];

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

function newReservation(
  id: string,
  departure: Departure,
  originFloor: number,
  destinationFloor: number,
): Reservation {
  const core = cores.find((c) => c.id === departure.coreId)!;
  return {
    id,
    status: 'ACTIVE',
    departure,
    core: {
      id: core.id,
      name: core.name,
      buildingName: buildings.find((b) => b.id === core.buildingId)!.name,
      floors: core.floors,
      hall: core.hall,
    },
    originFloor,
    destinationFloor,
    cancelCountsAsNoShow: false,
  };
}

/** El servidor calcula si cancelar ya cuenta como falta; acá se recalcula en cada lectura. */
const withCancelRule = (r: Reservation): Reservation => ({
  ...r,
  cancelCountsAsNoShow: Date.parse(r.departure.departsAt) - Date.now() < CANCEL_DEADLINE_MS,
});

// Estado en memoria: se reinicia al recargar la página.
let activeReservation: Reservation | null = newReservation('r-1', departuresFor('L2')[3], 0, 7);
/** Turno del último check-in, para "ya escaneado" y la encuesta de espera. */
let lastCheckedIn: string | null = null;
const answeredFeedback = new Set<string>();
let priorityRequest: PriorityRequest | null = null;
let notificationPreferences: NotificationPreferences = {
  departureReminder: false,
  spotReleased: false,
  delayCancellation: false,
  priorityAccess: false,
};

// Historial de ejemplo: dos viajes por día hacia atrás, con todos los resultados posibles.
const TRIP_RESULTS: TripResult[] = [
  'COMPLETED',
  'COMPLETED',
  'OTHER_ELEVATOR',
  'LATE',
  'CANCELLED',
  'NO_SHOW',
];
const trips: Trip[] = Array.from({ length: 24 }, (_, i) => {
  const day = Math.floor(i / 2) + 1;
  const departsAt = new Date(Date.now() - day * 86_400_000 - (i % 2) * 3 * 3_600_000);
  departsAt.setUTCSeconds(0, 0);
  const core = cores[i % cores.length];
  return {
    id: `t-${i + 1}`,
    departsAt: departsAt.toISOString(),
    durationMinutes: DEPARTURE_MINUTES,
    coreName: core.name,
    originFloor: 0,
    destinationFloor: (i % 6) + 3,
    result: TRIP_RESULTS[i % TRIP_RESULTS.length],
  };
});

/**
 * Códigos de prueba del check-in: "VENCIDO" es inválido, "OTRO" da otro ascensor y
 * "TARDE" fuera de hora; cualquier otro código no vacío sale cumplido.
 */
function checkInOutcome(code: string): CheckInResult['outcome'] {
  const normalized = code.trim().toUpperCase();
  if (!normalized || normalized.includes('VENCIDO')) {
    throw new ApiError(400, 'El código es inválido o ya venció. Escaneá el QR de nuevo.');
  }
  if (normalized.includes('OTRO')) return 'OTHER_ELEVATOR';
  if (normalized.includes('TARDE')) return 'LATE';
  return 'ON_TIME';
}

type Handler = (body: unknown, params: string[], query: URLSearchParams) => unknown;

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
      sessionStorage.setItem(SESSION_KEY, found.email);
      return { accessToken: `mock-${Date.now()}` } satisfies Session;
    },
  ],
  [
    'POST',
    /^\/auth\/refresh$/,
    () => {
      currentUser();
      return { accessToken: `mock-${Date.now()}` } satisfies Session;
    },
  ],
  [
    'POST',
    /^\/auth\/logout$/,
    () => {
      sessionStorage.removeItem(SESSION_KEY);
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
  ['GET', /^\/me$/, () => currentUser()],
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
        if (!exempt && Math.abs(floor - origin) <= LOW_FLOOR_MAX_DISTANCE) {
          return {
            floor,
            eligible: false,
            reason: `Para ${LOW_FLOOR_MAX_DISTANCE} piso usá la escalera.`,
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
      } satisfies TripPage;
    },
  ],
  ['GET', /^\/reservations\/active$/, () => activeReservation && withCancelRule(activeReservation)],
  [
    'GET',
    /^\/reservations\/([^/]+)$/,
    (_, [id]) => {
      if (activeReservation?.id !== id) throw new ApiError(404, 'No encontramos ese turno');
      return withCancelRule(activeReservation);
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
        `r-${Date.now()}`,
        { ...departure, occupied: departure.occupied + 1 },
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
      if (activeReservation?.id !== id) throw new ApiError(404, 'No encontramos ese turno');
      activeReservation = null;
    },
  ],
  [
    'POST',
    /^\/check-ins$/,
    (body) => {
      const { code } = body as CheckInRequest;
      if (!activeReservation) {
        throw new ApiError(
          409,
          lastCheckedIn
            ? 'Ya registraste el check-in de este turno.'
            : 'No tenés un turno activo para hacer check-in.',
        );
      }
      const outcome = checkInOutcome(code);
      const { id, core, departure } = activeReservation;
      const result: CheckInResult = {
        reservationId: id,
        outcome,
        checkedInAt: new Date().toISOString(),
        waitDeltaSeconds: outcome === 'LATE' ? 95 : 45,
        elevatorName: outcome === 'OTHER_ELEVATOR' ? 'Ascensor 3' : 'Ascensor 1',
        coreName: core.name,
        departsAt: departure.departsAt,
        durationMinutes: departure.durationMinutes,
      };
      activeReservation = null;
      lastCheckedIn = id;
      return result;
    },
  ],
  [
    'POST',
    /^\/check-ins\/([^/]+)\/wait-feedback$/,
    (body, [reservationId]) => {
      if (reservationId !== lastCheckedIn) throw new ApiError(404, 'No encontramos ese check-in');
      if (answeredFeedback.has(reservationId)) {
        throw new ApiError(409, 'Ya respondiste la encuesta de este viaje.');
      }
      if (!(body as WaitFeedbackRequest).range) throw new ApiError(400, 'Elegí un rango');
      answeredFeedback.add(reservationId);
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
        recentNoShows: trips.filter(
          (t) => t.result === 'NO_SHOW' && Date.parse(t.departsAt) >= since,
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
  [
    'GET',
    /^\/admin\/kpis$/,
    () =>
      ({
        baselinePercent: 43.5,
        avgWaitDeltaSeconds: 72,
        occupancyPercent: 68,
        qrCompliancePercent: 81,
        noShowsToday: 12,
        priorityAvgWaitSeconds: 95,
        cores: cores.map((c, i) => ({
          coreId: c.id,
          name: c.name,
          congestion: c.congestion,
          occupancyPercent: [92, 70, 41, 75, 38][i],
        })),
      }) satisfies AdminKpis,
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
): Promise<{ data: unknown } | undefined> {
  const [pathname, search] = path.split('?');
  for (const [routeMethod, pattern, handler] of routes) {
    const match = routeMethod === method ? pattern.exec(pathname) : null;
    if (!match) continue;
    await delay(latencyMs);
    // JSON como string; FormData (subida de archivos) pasa tal cual.
    const parsed: unknown = typeof body === 'string' ? JSON.parse(body) : (body ?? undefined);
    const params = match.slice(1).map(decodeURIComponent);
    return { data: handler(parsed, params, new URLSearchParams(search)) };
  }
  return undefined;
}
