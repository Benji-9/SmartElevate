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
  CongestionLevel,
  CreatedKiosk,
  CreateKioskRequest,
  Departure,
  Elevator,
  Kiosk,
  KioskList,
  LoginRequest,
  NoShowStatus,
  NotificationPreferences,
  PendingTeacher,
  PriorityUser,
  QrToken,
  RefreshRequest,
  RegisterRequest,
  Reservation,
  ReservationPage,
  ReservationStatus,
  ReserveRequest,
  ResetPasswordRequest,
  TokenResponse,
  User,
} from '../types/api';
import { ApiError } from './api';
import type { PriorityRequest, PriorityUploadRules } from '../types/pending';

// Parámetros "de la API": las pantallas los leen de las respuestas, no de acá.
const DEPARTURE_MINUTES = 2;
const CAPACITY = 10;
const WINDOW_MINUTES = 30;
/** La ventana de reserva cierra 2 min antes de la salida. */
const BOOKING_CLOSES_MS = 2 * 60_000;
/** Pisos bajos (`low_floors_threshold`): sin reserva con destino del 1 a este piso. */
const LOW_FLOORS_THRESHOLD = 4;
/** Se puede cancelar sin falta hasta este tiempo antes de la salida (`cancellation_deadline_before` del mock). */
const CANCEL_DEADLINE_MS = 60_000;
/** `cancelFreeUntil` de la reserva: salida − plazo, como lo calcula el backend. */
const cancelFreeUntil = (departureAt: string) =>
  new Date(Date.parse(departureAt) - CANCEL_DEADLINE_MS).toISOString();
const UPLOAD_RULES: PriorityUploadRules = {
  maxSizeBytes: 5 * 1024 * 1024,
  acceptedTypes: ['application/pdf', 'image/jpeg', 'image/png'],
};
const NO_SHOW_RULE = { threshold: 3, windowDays: 7, suspensionHours: 24 };
const TRIPS_PAGE_SIZE = 10;
/** `app.kiosk.qr-ttl` del backend (ADR 0020). */
const QR_TTL_MS = 30_000;
/** Lugares de cada salida común guardados para prioritarios (turnos.md §6). */
const PRIORITY_SEATS = 2;

function range(from: number, to: number) {
  return Array.from({ length: to - from + 1 }, (_, i) => from + i);
}

// Ascensores y pisos servidos del relevamiento (asignacion.md#ascensores-y-pisos-servidos).
const elevator = (
  code: string,
  core: string,
  floors: number[],
  usage: Elevator['usage'] = 'COMMON',
): Elevator => {
  const lima = core.startsWith('L');
  return {
    code,
    building: lima ? 'LIMA' : 'INDEPENDENCIA',
    core,
    coreName: `${lima ? 'Lima' : 'Independencia'} ${core.at(-1)}`,
    floors,
    usage,
    active: true,
  };
};
const ELEVATORS: Elevator[] = [
  ...['01', '02', '03', '04'].map((code) => elevator(code, 'L1', range(-3, 7))),
  // L2 tiene dos baterías: la baja (07–08) y la alta (35–36), que no para en el −1 ni en el 1.
  ...['07', '08'].map((code) => elevator(code, 'L2', range(-4, 5))),
  ...['35', '36'].map((code) => elevator(code, 'L2', [-2, 0, ...range(2, 10)])),
  ...['10', '11', '12', '13', '14', '15'].map((code) => elevator(code, 'L3', range(-3, 10))),
  // Dedicados: docentes y movilidad reducida.
  elevator('05', 'IND1', range(-4, 10), 'TEACHERS'),
  elevator('06', 'IND1', range(-4, 10), 'REDUCED_MOBILITY'),
  // No paran en el 1; único núcleo que llega al 11.
  ...['33', '34'].map((code) => elevator(code, 'IND2', [...range(-4, 0), ...range(2, 11)])),
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

/** Datos del núcleo que el contrato no tiene (congestión): solo para los mocks. */
type MockCore = {
  id: string;
  buildingId: string;
  name: string;
  congestion: CongestionLevel;
  estimatedWaitMinutes: number;
};

const cores: MockCore[] = [
  {
    id: 'L1',
    buildingId: 'LIMA',
    name: 'Lima 1',
    congestion: 'HIGH',
    estimatedWaitMinutes: 9,
  },
  {
    id: 'L2',
    buildingId: 'LIMA',
    name: 'Lima 2',
    congestion: 'MEDIUM',
    estimatedWaitMinutes: 5,
  },
  {
    id: 'L3',
    buildingId: 'LIMA',
    name: 'Lima 3',
    congestion: 'LOW',
    estimatedWaitMinutes: 2,
  },
  {
    id: 'IND1',
    buildingId: 'INDEPENDENCIA',
    name: 'Independencia 1',
    congestion: 'LOW',
    estimatedWaitMinutes: 1,
  },
  {
    id: 'IND2',
    buildingId: 'INDEPENDENCIA',
    name: 'Independencia 2',
    congestion: 'LOW',
    estimatedWaitMinutes: 3,
  },
];

// Ocupación de ejemplo por salida, rotada por ascensor: hay llenas para probar TURN_FULL y casi
// llenas (8–9) donde lo que queda es de prioritarios.
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

const STEP_MS = DEPARTURE_MINUTES * 60_000;
/** El id numérico codifica la salida: hora (en pasos de 2 min) × 100 + índice del ascensor. */
const departureIdOf = (at: number, elevatorIndex: number) => (at / STEP_MS) * 100 + elevatorIndex;
/** Primera salida dentro de la ventana (cierra 2 min antes). */
const firstSlot = () => Math.ceil((Date.now() + BOOKING_CLOSES_MS) / STEP_MS) * STEP_MS;

// Estado en memoria: se reinicia al recargar la página.
let reservations: Reservation[] = [];
let reservationCounter = 0;

const activeReservation = () => reservations.find((r) => r.status === 'ACTIVE');

/** Lo que `ReservationResponse` agrega del ascensor: núcleo y edificio. */
function placeOf(elevatorCode: string) {
  const e = ELEVATORS.find((x) => x.code === elevatorCode)!;
  return {
    elevator: e.code,
    coreName: e.coreName,
    buildingName: buildings.find((b) => b.code === e.building)!.name,
  };
}

/** El servidor calcula si cancelar ya cuenta como falta; acá se recalcula en cada lectura. */
const view = (r: Reservation): Reservation => ({
  ...r,
  cancelCountsAsNoShow:
    r.status === 'ACTIVE' && Date.parse(r.departureAt) - Date.now() < CANCEL_DEADLINE_MS,
});

function occupied(departureId: number) {
  const base = OCCUPANCY[(Math.floor(departureId / 100) + (departureId % 100)) % OCCUPANCY.length];
  const booked = reservations.filter(
    (r) => r.departureId === departureId && r.status === 'ACTIVE',
  ).length;
  return Math.min(CAPACITY, base + booked);
}

/** Como en el backend: los dedicados solo para su categoría vigente. */
const canUse = (e: Elevator, user: User) =>
  e.usage === 'COMMON' ||
  (e.usage === 'TEACHERS' && user.priority === 'TEACHER') ||
  (e.usage === 'REDUCED_MOBILITY' && user.priority === 'REDUCED_MOBILITY');

// ponytail: los 2 lugares de prioritarios se descuentan siempre a los comunes; el backend
// además los libera 3 min antes y cuenta los ya tomados por prioritarios.
function departureFor(id: number, user: User): Departure {
  const e = ELEVATORS[id % 100];
  const free = CAPACITY - occupied(id);
  const held = e.usage === 'COMMON' && user.priority === 'NONE' ? PRIORITY_SEATS : 0;
  return {
    id,
    elevator: e.code,
    building: e.building,
    departureAt: new Date(Math.floor(id / 100) * STEP_MS).toISOString(),
    capacity: CAPACITY,
    remainingSeats: Math.max(0, free - held),
    status: 'SCHEDULED',
  };
}

/** `GET /departures`: la ventana de reserva de los ascensores que el usuario puede usar. */
function bookable(user: User, building: string | null, elevatorCode: string | null) {
  const first = firstSlot();
  const times = range(0, WINDOW_MINUTES / DEPARTURE_MINUTES)
    .map((i) => first + i * STEP_MS)
    .filter((at) => at <= Date.now() + WINDOW_MINUTES * 60_000);
  return times.flatMap((at) =>
    ELEVATORS.flatMap((e, index) =>
      canUse(e, user) &&
      (!building || e.building === building.toUpperCase()) &&
      (!elevatorCode || e.code === elevatorCode)
        ? [departureFor(departureIdOf(at, index), user)]
        : [],
    ),
  );
}

const rejected = (status: number, code: string, message: string) =>
  new ApiError(status, message, [], undefined, code);

/** `POST /reservations`, con los rechazos y los `code` del backend (ReservationService). */
function book(user: User, { departureId, originFloor, destinationFloor }: ReserveRequest) {
  const e = Number.isInteger(departureId) ? ELEVATORS[departureId % 100] : undefined;
  if (!e) throw rejected(404, 'DEPARTURE_NOT_FOUND', 'La salida no existe.');
  if (!canUse(e, user)) {
    throw rejected(
      422,
      'ELEVATOR_NOT_BOOKABLE',
      e.usage === 'TEACHERS'
        ? 'Ese ascensor es exclusivo para docentes.'
        : 'Ese ascensor es exclusivo para personas con movilidad reducida.',
    );
  }
  const departure = departureFor(departureId, user);
  const at = Date.parse(departure.departureAt);
  if (at < Date.now() + BOOKING_CLOSES_MS || at > Date.now() + WINDOW_MINUTES * 60_000) {
    throw rejected(409, 'OUTSIDE_BOOKING_WINDOW', 'La salida está fuera de la ventana de reserva.');
  }
  if (originFloor === destinationFloor) {
    throw rejected(422, 'SAME_FLOOR', 'El piso de origen y el de destino son el mismo.');
  }
  const building = buildings.find((b) => b.code === e.building)!;
  const inRange = (f: number) => f >= building.minFloor && f <= building.maxFloor;
  if (!inRange(originFloor) || !inRange(destinationFloor)) {
    throw rejected(422, 'FLOOR_OUT_OF_RANGE', 'Alguno de los pisos no existe en el edificio.');
  }
  if (!e.floors.includes(originFloor) || !e.floors.includes(destinationFloor)) {
    throw rejected(422, 'FLOOR_NOT_SERVED', 'Ese ascensor no para en alguno de los pisos.');
  }
  if (
    user.priority !== 'REDUCED_MOBILITY' &&
    destinationFloor >= 1 &&
    destinationFloor <= LOW_FLOORS_THRESHOLD
  ) {
    throw rejected(
      422,
      'LOW_FLOOR_DESTINATION',
      `Para ir a los pisos 1 a ${LOW_FLOORS_THRESHOLD} usá la escalera.`,
    );
  }
  if (activeReservation()) {
    throw rejected(409, 'ACTIVE_LIMIT_REACHED', 'Ya tenés una reserva activa.');
  }
  if (departure.remainingSeats <= 0) {
    throw rejected(
      409,
      'TURN_FULL',
      occupied(departureId) < CAPACITY
        ? 'Los lugares que quedan están reservados para prioritarios. Elegí otra salida.'
        : 'La salida está llena. Elegí otra.',
    );
  }
  const reservation: Reservation = {
    id: ++reservationCounter,
    departureId,
    ...placeOf(e.code),
    departureAt: departure.departureAt,
    cancelFreeUntil: cancelFreeUntil(departure.departureAt),
    seatNumber: occupied(departureId) + 1,
    originFloor,
    destinationFloor,
    status: 'ACTIVE',
    createdAt: new Date().toISOString(),
    cancelCountsAsNoShow: false,
    checkInOutcome: null,
  };
  reservations = [...reservations, reservation];
  return view(reservation);
}

// Arranca con un turno activo en el ascensor 33 (Independencia 2), PB → 7.
{
  const at = firstSlot() + 3 * STEP_MS;
  const departureId = departureIdOf(
    at,
    ELEVATORS.findIndex((e) => e.code === '33'),
  );
  reservations = [
    {
      id: ++reservationCounter,
      departureId,
      ...placeOf('33'),
      departureAt: new Date(at).toISOString(),
      cancelFreeUntil: cancelFreeUntil(new Date(at).toISOString()),
      seatNumber: occupied(departureId) + 1,
      originFloor: 0,
      destinationFloor: 7,
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
      cancelCountsAsNoShow: false,
      checkInOutcome: null,
    },
  ];
}
/** Turno del último check-in, para "ya escaneado". */
let lastCheckedIn: number | null = null;
let priorityRequest: PriorityRequest | null = null;
let notificationPreferences: NotificationPreferences = {
  // Como el servidor: todos los avisos activos por defecto.
  departureReminder: true,
  elevatorOutOfService: true,
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
const pastTrips: Reservation[] = Array.from({ length: 24 }, (_, i) => {
  const day = Math.floor(i / 2) + 1;
  const departsAt = new Date(Date.now() - day * 86_400_000 - (i % 2) * 3 * 3_600_000);
  departsAt.setUTCSeconds(0, 0);
  const [status, checkInOutcome] = TRIP_RESULTS[i % TRIP_RESULTS.length];
  return {
    id: 1000 - i,
    departureId: 0,
    ...placeOf(ELEVATORS[(i * 3) % ELEVATORS.length].code),
    departureAt: departsAt.toISOString(),
    cancelFreeUntil: cancelFreeUntil(departsAt.toISOString()),
    seatNumber: status.startsWith('CANCELLED') ? null : 3,
    originFloor: 0,
    // 5 a 7: fuera de los pisos bajos y servidos por todos los núcleos.
    destinationFloor: (i % 3) + 5,
    status,
    createdAt: new Date(departsAt.getTime() - 10 * 60_000).toISOString(),
    cancelCountsAsNoShow: false,
    checkInOutcome,
  };
});
/** Historial como el backend: las de esta sesión y las de ejemplo, la salida más reciente primero. */
const history = () =>
  [...reservations, ...pastTrips].sort(
    (a, b) => Date.parse(b.departureAt) - Date.parse(a.departureAt) || b.id - a.id,
  );

/**
 * Tokens de prueba del check-in: los del kiosco simulado (`mock-qr-<ascensor>-…`) traen su
 * ascensor; "VENCIDO" es inválido, "OTRO" da otro ascensor (34, o 33 si el turno es del 34) y
 * "TARDE" fuera de hora. Cualquier otro token no vacío (p. ej. el "DEMO" del modo demo) es del
 * ascensor del turno.
 */
function scanned(token: string, reserved: string) {
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
    (normalized.includes('OTRO') ? (reserved === '34' ? '33' : '34') : reserved);
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
  ['GET', /^\/elevators$/, () => ELEVATORS],
  [
    'GET',
    /^\/departures$/,
    (_, __, query) => bookable(currentUser(), query.get('building'), query.get('elevator')),
  ],
  [
    'GET',
    /^\/reservations\/history$/,
    (_, __, query) => {
      const start = Number(query.get('cursor') ?? 0);
      const end = start + TRIPS_PAGE_SIZE;
      const all = history();
      return {
        items: all.slice(start, end).map(view),
        nextCursor: end < all.length ? String(end) : null,
      } satisfies ReservationPage;
    },
  ],
  [
    'GET',
    /^\/reservations\/active$/,
    () => {
      // 204 sin cuerpo si no hay turno activo.
      const active = activeReservation();
      return active && view(active);
    },
  ],
  [
    'GET',
    /^\/reservations\/([^/]+)$/,
    (_, [id]) => {
      const found = history().find((r) => String(r.id) === id);
      if (!found) throw rejected(404, 'RESERVATION_NOT_FOUND', 'La reserva no existe.');
      return view(found);
    },
  ],
  ['POST', /^\/reservations$/, (body) => book(currentUser(), body as ReserveRequest)],
  [
    'DELETE',
    /^\/reservations\/([^/]+)$/,
    (_, [id]) => {
      const found = reservations.find((r) => String(r.id) === id);
      if (!found) throw rejected(404, 'RESERVATION_NOT_FOUND', 'La reserva no existe.');
      // Idempotente: solo una activa cambia de estado.
      if (found.status !== 'ACTIVE') return;
      const late = Date.parse(found.departureAt) - Date.now() < CANCEL_DEADLINE_MS;
      reservations = reservations.map((r) =>
        r === found ? { ...r, status: late ? 'CANCELLED_LATE' : 'CANCELLED', seatNumber: null } : r,
      );
    },
  ],
  [
    'POST',
    /^\/check-ins$/,
    (body) => {
      const active = activeReservation();
      const { elevator, late } = scanned((body as CheckInRequest).token, active?.elevator ?? '33');
      if (!active) {
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
      const status = late ? 'NO_SHOW' : 'USED';
      const outcome = late ? 'LATE' : elevator === active.elevator ? 'ON_TIME' : 'OTHER_ELEVATOR';
      const result: CheckIn = {
        reservationId: active.id,
        outcome,
        reservationStatus: status,
        elevator,
        reservedElevator: active.elevator,
        departureAt: active.departureAt,
        checkedInAt: new Date().toISOString(),
        source: 'SIMULATED',
      };
      lastCheckedIn = active.id;
      reservations = reservations.map((r) =>
        r === active ? { ...r, status, checkInOutcome: outcome } : r,
      );
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
        recentNoShows: history().filter(
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
