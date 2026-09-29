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
  Core,
  Departure,
  LoginRequest,
  PriorityRequest,
  Reservation,
  ReserveRequest,
  Session,
  User,
} from '../types/pending';

// Parámetros "de la API": las pantallas los leen de las respuestas, no de acá.
const DEPARTURE_MINUTES = 2;
const CAPACITY = 10;
const WINDOW_MINUTES = 30;

// Usuarios de ejemplo: cualquier contraseña sirve, salvo "incorrecta".
const users: User[] = [
  {
    id: 'u-1',
    email: 'ana.perez@uade.edu.ar',
    fullName: 'Ana Pérez',
    legajo: '1099999',
    role: 'USER',
    priority: 'NONE',
  },
  {
    id: 'u-2',
    email: 'admin@uade.edu.ar',
    fullName: 'Admin UADE',
    legajo: '1000001',
    role: 'ADMIN',
    priority: 'NONE',
  },
  {
    id: 'u-3',
    email: 'sin.verificar@uade.edu.ar',
    fullName: 'Sin Verificar',
    legajo: '1000002',
    role: 'USER',
    priority: 'NONE',
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
  },
  {
    id: 'L2',
    buildingId: 'LIMA',
    name: 'Lima 2',
    floors: range(0, 10),
    congestion: 'MEDIUM',
    estimatedWaitMinutes: 5,
  },
  {
    id: 'L3',
    buildingId: 'LIMA',
    name: 'Lima 3',
    floors: range(0, 6),
    congestion: 'LOW',
    estimatedWaitMinutes: 2,
  },
  {
    id: 'IND1',
    buildingId: 'IND',
    name: 'Independencia 1',
    floors: range(-3, 10),
    congestion: 'MEDIUM',
    estimatedWaitMinutes: 6,
  },
  {
    id: 'IND2',
    buildingId: 'IND',
    name: 'Independencia 2',
    floors: range(0, 10),
    congestion: 'LOW',
    estimatedWaitMinutes: 3,
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

// Estado en memoria: se reinicia al recargar la página.
let activeReservation: Reservation | null = {
  id: 'r-1',
  status: 'ACTIVE',
  departure: departuresFor('L2')[3],
  originFloor: 0,
  destinationFloor: 7,
};

type Handler = (body: unknown, params: string[]) => unknown;

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
  ['GET', /^\/me$/, () => currentUser()],
  ['GET', /^\/buildings$/, () => buildings],
  ['GET', /^\/cores$/, () => cores],
  ['GET', /^\/cores\/([^/]+)\/departures$/, (_, [coreId]) => departuresFor(coreId)],
  ['GET', /^\/reservations\/active$/, () => activeReservation],
  [
    'POST',
    /^\/reservations$/,
    (body) => {
      const { departureId, originFloor, destinationFloor } = body as ReserveRequest;
      const departure = findDeparture(departureId);
      if (departure.occupied >= departure.capacity) {
        throw new ApiError(409, 'La salida está llena. Elegí otra.');
      }
      activeReservation = {
        id: `r-${Date.now()}`,
        status: 'ACTIVE',
        departure: { ...departure, occupied: departure.occupied + 1 },
        originFloor,
        destinationFloor,
      };
      return activeReservation;
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
      if (!activeReservation) throw new ApiError(409, 'No tenés un turno activo');
      if (!code.trim()) throw new ApiError(400, 'Código inválido o vencido');
      const result: CheckInResult = {
        reservationId: activeReservation.id,
        outcome: 'ON_TIME',
        checkedInAt: new Date().toISOString(),
        waitDeltaSeconds: 45,
      };
      activeReservation = null;
      return result;
    },
  ],
  [
    'GET',
    /^\/priority-requests\/me$/,
    () =>
      ({
        category: 'TEACHER',
        status: 'PENDING',
        submittedAt: new Date(Date.now() - 2 * 86_400_000).toISOString(),
        expiresAt: null,
      }) satisfies PriorityRequest,
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
  const pathname = path.split('?')[0];
  for (const [routeMethod, pattern, handler] of routes) {
    const match = routeMethod === method ? pattern.exec(pathname) : null;
    if (!match) continue;
    await delay(latencyMs);
    const parsed: unknown = typeof body === 'string' ? JSON.parse(body) : undefined;
    return { data: handler(parsed, match.slice(1).map(decodeURIComponent)) };
  }
  return undefined;
}
