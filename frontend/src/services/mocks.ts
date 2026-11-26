// Datos simulados para desarrollar sin backend (#42). Solo se cargan en `vite dev`
// con VITE_USE_MOCKS (ver api.ts); nunca entran al build de producción.
// Para apagar un endpoint cuando el real esté listo, borrá su ruta de `routes`:
// lo que no matchea acá sigue de largo al backend.
//
// Reglas del servidor que SÍ emulan (docs/reglas/turnos.md y check-in-qr.md): cancelación sin
// falta hasta 10 min antes (CANCELLED vs CANCELLED_LATE, `cancelFreeUntil`, `cancelCountsAsNoShow`),
// cancelar después de la salida (DEPARTURE_ALREADY_PASSED / ALREADY_CHECKED_IN), 2 lugares de
// prioritarios en ascensores comunes liberados 3 min antes, ELEVATOR_NOT_BOOKABLE, tope de 8
// reservas por día (la cancelación sin falta lo devuelve), suspensión de 24 h tras 3 faltas en
// 7 días (NO_SHOW_SUSPENDED; prioritarios exentos) y la ventana de check-in (−1 min a +2 min,
// ON_TIME hasta +60 s) con los códigos de `POST /check-ins`.
//
// Lo que NO emulan (el backend sí):
// - Zonas de pisos (ADR 0025): la oferta de cada hora es la salida con más lugares libres.
// - Job de no-show: una reserva ACTIVE nunca pasa sola a NO_SHOW ni deja de ser "activa".
// - Concurrencia y cupo atómico; la ocupación de otros usuarios es fija y de ejemplo.
// - Lugares tomados por prioritarios: el cupo reservado se descuenta siempre completo hasta
//   su liberación (no se compensa con otros prioritarios que ya reservaron).
// - Salidas que se generan por horario, `sameDay` de la ventana y los parámetros de `app_config`.
// - Check-in simulado: todo check-in sale como `REAL` y sus faltas cuentan.
// - Un solo turno activo por usuario se chequea sin distinguir usuarios (hay un estado global).
// - Tokens de QR: no se firman; los tokens `DEMO`, `TARDE` y `OTRO` saltean la ventana horaria
//   para poder probar cada resultado sin esperar la hora de salida.
// Para probar sin QR: con `?demo=1&checkin=tarde` o `&checkin=otro` en la URL, el botón "Simular
// escaneo del QR" da fuera de hora u otro ascensor en vez de a tiempo.
// Edificios, núcleos y pisos del relevamiento (docs/reglas/asignacion.md, #22); congestión,
// KPIs y ocupación siguen siendo de ejemplo.
import type {
  AdminElevator,
  AdminKpis,
  AdminKpisQuery,
  AdminShift,
  AdminUser,
  AdminUserPage,
  DeclaredUserType,
  Building,
  CheckIn,
  CheckInRequest,
  CongestionLevel,
  CongestionSnapshot,
  CreatedKiosk,
  CreateKioskRequest,
  Departure,
  Elevator,
  ElevatorServiceResult,
  Kiosk,
  KioskList,
  LoginRequest,
  NoShowStatus,
  NotificationPreferences,
  PendingTeacher,
  Role,
  PriorityUser,
  QrToken,
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

// Parámetros "de la API": las pantallas los leen de las respuestas, no de acá.
const DEPARTURE_MINUTES = 2;
const CAPACITY = 10;
const WINDOW_MINUTES = 30;
/** La ventana de reserva cierra 2 min antes de la salida. */
const BOOKING_CLOSES_MS = 2 * 60_000;
/** Pisos bajos (`low_floors_threshold`): sin reserva con destino del 1 a este piso. */
const LOW_FLOORS_THRESHOLD = 4;
/** Se puede cancelar sin falta hasta este tiempo antes de la salida (`cancellation_deadline_before`, V18). */
const CANCEL_DEADLINE_MS = 10 * 60_000;
/** Los lugares de prioritarios sin usar se liberan a los comunes este tiempo antes de la salida. */
const PRIORITY_RELEASE_MS = 3 * 60_000;
/** `max_daily_bookings_per_user`. */
const MAX_DAILY_BOOKINGS = 8;
/** Check-in: desde 1 min antes hasta 2 min después; a tiempo hasta +60 s (`no_show_after`). */
const CHECK_IN_OPENS_MS = 60_000;
const CHECK_IN_CLOSES_MS = 2 * 60_000;
const NO_SHOW_AFTER_MS = 60_000;
/** `cancelFreeUntil` de la reserva: salida − plazo, como lo calcula el backend. */
const cancelFreeUntil = (departureAt: string) =>
  new Date(Date.parse(departureAt) - CANCEL_DEADLINE_MS).toISOString();
/** `checkInOpensAt` de la reserva: salida − apertura del check-in, como lo calcula el backend. */
const checkInOpensAt = (departureAt: string) =>
  new Date(Date.parse(departureAt) - CHECK_IN_OPENS_MS).toISOString();
const NO_SHOW_RULE = { threshold: 3, windowDays: 7, suspensionHours: 24 };
const TRIPS_PAGE_SIZE = 10;
/**
 * `app.kiosk.qr-shift-boundaries` del backend (07, 13, 18 y 23 h de Buenos Aires, ADR 0026) en
 * horas UTC: Argentina es UTC−3 todo el año.
 */
const QR_SHIFT_HOURS_UTC = [2, 10, 16, 21];
/** El próximo cambio de turno: ahí vence el QR del kiosco. */
function nextQrShift(now = Date.now()) {
  const date = new Date(now);
  // Pasadas las 21 h UTC, el siguiente es el primero de mañana (`setUTCHours` pasa de día).
  const hour = QR_SHIFT_HOURS_UTC.find((h) => h > date.getUTCHours()) ?? 24 + QR_SHIFT_HOURS_UTC[0];
  return date.setUTCHours(hour, 0, 0, 0);
}
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
// Ascensores fuera de servicio (#134): no se ofrecen y `GET /elevators` los devuelve con `active: false`.
const outOfService = new Set<string>();
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
// Usuarios que ve el buscador de roles (#215). El primero es el admin del mock (ver `users`).
const adminUsers: AdminUser[] = [
  {
    id: 'u-2',
    fullName: 'Admin UADE',
    email: 'admin@uade.edu.ar',
    legajo: '1000001',
    role: 'ADMIN',
    declaredUserType: 'STUDENT',
    emailVerified: true,
  },
  {
    id: '0b9f1c2e-4d1a-4c3e-9a51-1f2e3d4c5b11',
    fullName: 'Carla Díaz',
    email: 'carla.diaz@uade.edu.ar',
    legajo: '1088888',
    role: 'USER',
    declaredUserType: 'STUDENT',
    emailVerified: true,
  },
  {
    id: '0b9f1c2e-4d1a-4c3e-9a51-1f2e3d4c5b12',
    fullName: 'Lucía Fernández',
    email: 'lucia.fernandez@uade.edu.ar',
    legajo: '1077777',
    role: 'REVIEWER',
    declaredUserType: 'TEACHER',
    emailVerified: true,
  },
  {
    id: '0b9f1c2e-4d1a-4c3e-9a51-1f2e3d4c5b13',
    fullName: 'Mateo Ríos',
    email: 'mateo.rios@uade.edu.ar',
    legajo: '1066666',
    role: 'USER',
    declaredUserType: 'STUDENT',
    emailVerified: false,
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
  {
    id: 'u-4',
    email: 'docente@uade.edu.ar',
    fullName: 'Docente UADE',
    legajo: '1000003',
    role: 'USER',
    priority: 'TEACHER',
    priorityUntil: '2027-03-31T02:59:59Z',
    declaredUserType: 'TEACHER',
  },
];

// Cuentas que todavía no hicieron click en el link de verificación.
const unverified = new Set(['sin.verificar@uade.edu.ar']);

// Simula la cookie del refresh del servidor: sobrevive a recargar la página y la ven las pestañas
// nuevas (localStorage). La copia en sessionStorage deja una cuenta distinta en cada pestaña.
const SESSION_KEY = 'smartelevate-mock-session';

const sessionEmail = () => sessionStorage.getItem(SESSION_KEY) ?? localStorage.getItem(SESSION_KEY);

function tokensFor(email: string): TokenResponse {
  sessionStorage.setItem(SESSION_KEY, email);
  localStorage.setItem(SESSION_KEY, email);
  return { accessToken: `mock-${Date.now()}`, tokenType: 'Bearer', expiresIn: 900 };
}

function currentUser(): User {
  const email = sessionEmail();
  const found = users.find((u) => u.email === email);
  if (!found) throw new ApiError(401, 'Sesión vencida');
  return found;
}

/** Núcleos con su ocupación de ejemplo y qué parte de las reservas del campus se lleva cada uno. */
const cores = [
  { id: 'L1', buildingId: 'LIMA', name: 'Lima 1', occupancy: 92, share: 0.3 },
  { id: 'L2', buildingId: 'LIMA', name: 'Lima 2', occupancy: 68, share: 0.2 },
  { id: 'L3', buildingId: 'LIMA', name: 'Lima 3', occupancy: 41, share: 0.3 },
  { id: 'IND1', buildingId: 'INDEPENDENCIA', name: 'Independencia 1', occupancy: 20, share: 0.05 },
  { id: 'IND2', buildingId: 'INDEPENDENCIA', name: 'Independencia 2', occupancy: 74, share: 0.15 },
];
const coresByName = [...cores].sort((a, b) => a.name.localeCompare(b.name));
/** `congestion_medium_from` y `congestion_high_from` (kpis.md). */
const levelOf = (percent: number): CongestionLevel =>
  percent >= 85 ? 'HIGH' : percent >= 60 ? 'MEDIUM' : 'LOW';

/** `GET /congestion`: refresca cada salida (`refreshAfterSeconds`). */
const congestion = (): CongestionSnapshot => ({
  updatedAt: new Date().toISOString(),
  refreshAfterSeconds: DEPARTURE_MINUTES * 60,
  cores: coresByName.map((c) => ({
    coreId: c.id,
    name: c.name,
    level: levelOf(c.occupancy),
    occupancyPercent: c.occupancy,
  })),
});

/** Turnos de cursada de kpis.md#filtros. */
const SHIFTS: AdminShift[] = [
  { id: 'MORNING', name: 'Turno mañana', startsAt: '07:00', endsAt: '13:00' },
  { id: 'AFTERNOON', name: 'Turno tarde', startsAt: '13:00', endsAt: '18:00' },
  { id: 'NIGHT', name: 'Turno noche', startsAt: '18:00', endsAt: '22:30' },
];
/** Reservas de ejemplo de un día en todo el campus, por hora desde las 7: picos al entrar a cursar. */
const DAILY_BY_HOUR = [60, 140, 110, 60, 50, 80, 100, 55, 40, 45, 85, 130, 105, 50, 35, 15];
const FIRST_HOUR = 7;
const PERIOD_DAYS: Record<AdminKpisQuery['period'], number> = { TODAY: 1, WEEK: 7, MONTH: 30 };
const CHECK_IN_PERCENT = 86.4;

const round1 = (n: number) => Math.round(n * 10) / 10;
const totalOf = (rows: { reservations: number }[]) =>
  rows.reduce((sum, row) => sum + row.reservations, 0);

/** `GET /admin/kpis`: datos de ejemplo que cierran entre sí. La espera va en `null` como en el real. */
function adminKpis(query: URLSearchParams): AdminKpis {
  const days = PERIOD_DAYS[query.get('period') as AdminKpisQuery['period']] ?? 1;
  const building = query.get('buildingId');
  const shift = SHIFTS.find((s) => s.id === query.get('shiftId'));
  const hours = DAILY_BY_HOUR.map((demand, i) => ({ hour: FIRST_HOUR + i, demand })).filter(
    ({ hour }) => {
      const at = `${String(hour).padStart(2, '0')}:00`;
      return !shift || (at >= shift.startsAt && at < shift.endsAt);
    },
  );
  const selected = coresByName.filter((c) => !building || c.buildingId === building);
  const seats = (demand: number, share: number) => Math.round(demand * share) * days;
  const reservationsByHour = hours.map(({ hour, demand }) => ({
    hour,
    reservations: selected.reduce((sum, c) => sum + seats(demand, c.share), 0),
  }));
  const rows = selected.map((c) => ({
    coreId: c.id,
    name: c.name,
    reservations: hours.reduce((sum, h) => sum + seats(h.demand, c.share), 0),
    occupancyPercent: c.occupancy,
    avgWaitSeconds: null,
    congestion: levelOf(c.occupancy),
  }));
  const reservations = totalOf(rows);
  // Personas por salida: la ocupación de cada núcleo pesada por sus reservas.
  const avgOccupancy = round1(
    rows.reduce((sum, r) => sum + r.reservations * r.occupancyPercent, 0) /
      reservations /
      (100 / CAPACITY),
  );
  const dayMs = 86_400_000;
  // Buenos Aires es UTC−3 todo el año.
  const to = Date.parse(`${campusDay(Date.now())}T00:00:00-03:00`) + dayMs;
  const from = to - days * dayMs;
  const iso = (at: number) => new Date(at).toISOString();
  return {
    from: iso(from),
    to: iso(to),
    avgWaitSeconds: null,
    wait5To10Percent: null,
    baselinePercent: 43.5,
    reservations,
    checkIns: Math.round((reservations * CHECK_IN_PERCENT) / 100),
    checkInPercent: CHECK_IN_PERCENT,
    avgOccupancy,
    capacity: CAPACITY,
    reservationsByHour,
    cores: rows,
    previous: {
      from: iso(from - days * dayMs),
      to: iso(from),
      reservations: Math.round(reservations * 0.92),
      checkInPercent: CHECK_IN_PERCENT - 2.3,
      avgOccupancy: round1(avgOccupancy - 0.4),
      avgWaitSeconds: null,
      wait5To10Percent: null,
    },
  };
}

// Ocupación de ejemplo por salida, rotada por ascensor: hay llenas para probar TURN_FULL y casi
// llenas (8–9) donde lo que queda es de prioritarios.
const OCCUPANCY = [4, 10, 7, 2, 9, 0, 5, 3, 8, 1, 6, 2, 0, 4, 1];
// Una de cada 7 salidas (cada 14 min) va llena en todos los ascensores: la oferta de cada hora es la
// del más libre, así que sin esto la franja "Completo" no se vería nunca.
const FULL_EVERY = 7;

// Misma forma que GET /api/buildings: los núcleos van con código y nombre visible.
const coresOf = (buildingCode: string) =>
  cores.filter((c) => c.buildingId === buildingCode).map((c) => ({ code: c.id, name: c.name }));

const buildings: Building[] = [
  {
    code: 'LIMA',
    name: 'Lima',
    minFloor: -4,
    maxFloor: 10,
    cores: coresOf('LIMA'),
    lowFloorsThreshold: LOW_FLOORS_THRESHOLD,
  },
  {
    code: 'INDEPENDENCIA',
    name: 'Independencia',
    minFloor: -4,
    maxFloor: 11,
    cores: coresOf('INDEPENDENCIA'),
    lowFloorsThreshold: LOW_FLOORS_THRESHOLD,
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

/** `GET /admin/elevators`: todos, con las reservas activas que se cancelarían. */
function adminElevators(): AdminElevator[] {
  return ELEVATORS.map((e) => ({
    code: e.code,
    coreName: e.coreName,
    building: e.building,
    usage: e.usage,
    active: !outOfService.has(e.code),
    upcomingReservations: reservations.filter((r) => r.status === 'ACTIVE' && r.elevator === e.code)
      .length,
  }));
}

/** Marcar / reactivar: lo mismo que el backend (idempotente, cancela sin falta y libera el lugar). */
function setService(code: string, active: boolean): ElevatorServiceResult {
  if (!ELEVATORS.some((e) => e.code === code)) throw new ApiError(404, 'Ascensor inexistente');
  let cancelled = 0;
  if (!active && !outOfService.has(code)) {
    reservations = reservations.map((r) => {
      if (r.status !== 'ACTIVE' || r.elevator !== code) return r;
      cancelled += 1;
      return { ...r, status: 'CANCELLED', seatNumber: null };
    });
  }
  if (active) outOfService.delete(code);
  else outOfService.add(code);
  return { code, active, cancelledReservations: cancelled };
}

/** El servidor calcula si cancelar ya cuenta como falta; acá se recalcula en cada lectura. */
const view = (r: Reservation): Reservation => ({
  ...r,
  cancelCountsAsNoShow:
    r.status === 'ACTIVE' && Date.parse(r.departureAt) - Date.now() < CANCEL_DEADLINE_MS,
});

/** Solo cancelar libera el lugar: `USED` y `NO_SHOW` lo conservan (check-in-qr.md). */
const holdsSeat = (r: Reservation) =>
  r.status === 'ACTIVE' || r.status === 'USED' || r.status === 'NO_SHOW';

function occupied(departureId: number) {
  const slot = Math.floor(departureId / 100);
  const base =
    slot % FULL_EVERY === 0 ? CAPACITY : OCCUPANCY[(slot + (departureId % 100)) % OCCUPANCY.length];
  const booked = reservations.filter((r) => r.departureId === departureId && holdsSeat(r)).length;
  return Math.min(CAPACITY, base + booked);
}

/** Como en el backend: los dedicados solo para su categoría vigente. */
const canUse = (e: Elevator, user: User) =>
  e.usage === 'COMMON' ||
  (e.usage === 'TEACHERS' && user.priority === 'TEACHER') ||
  (e.usage === 'REDUCED_MOBILITY' && user.priority === 'REDUCED_MOBILITY');

// ponytail: los 2 lugares de prioritarios se descuentan completos a los comunes hasta que se
// liberan (3 min antes); el backend además descuenta los que ya tomaron otros prioritarios.
function departureFor(id: number, user: User): Departure {
  const e = ELEVATORS[id % 100];
  const free = CAPACITY - occupied(id);
  const departureAt = Math.floor(id / 100) * STEP_MS;
  const released = Date.now() >= departureAt - PRIORITY_RELEASE_MS;
  const held = e.usage === 'COMMON' && user.priority === 'NONE' && !released ? PRIORITY_SEATS : 0;
  return {
    id,
    elevator: e.code,
    building: e.building,
    departureAt: new Date(departureAt).toISOString(),
    capacity: CAPACITY,
    remainingSeats: Math.max(0, free - held),
    bookedSeats: CAPACITY - free,
    bookingClosesAt: new Date(departureAt - BOOKING_CLOSES_MS).toISOString(),
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
      !outOfService.has(e.code) &&
      canUse(e, user) &&
      (!building || e.building === building.toUpperCase()) &&
      (!elevatorCode || e.code === elevatorCode)
        ? [departureFor(departureIdOf(at, index), user)]
        : [],
    ),
  );
}

/**
 * `GET /departures?core=&origin=&destination=`: una salida por hora entre las del núcleo que paran
 * en ambos pisos. Como el servidor, no rechaza pisos bajos ni origen igual a destino.
 */
// ponytail: gana la de más lugares libres (a igualdad, el menor código), sin zonas ni afinidad
// (ADR 0025, ZoneOfferResolver en el backend). Portarlas si hay que probar el agrupamiento sin backend.
function offer(user: User, core: string, origin: number, destination: number): Departure[] {
  const codes = new Set(
    ELEVATORS.filter(
      (e) =>
        e.core === core.toUpperCase() &&
        e.floors.includes(origin) &&
        e.floors.includes(destination),
    ).map((e) => e.code),
  );
  const byTime = new Map<string, Departure>();
  for (const d of bookable(user, null, null)) {
    if (!codes.has(d.elevator)) continue;
    const best = byTime.get(d.departureAt);
    if (!best || d.remainingSeats > best.remainingSeats) byTime.set(d.departureAt, d);
  }
  return [...byTime.values()];
}

/** Sin núcleo ni pisos, la ventana entera; con los tres, la oferta; con algunos, 400. */
function departuresFor(user: User, query: URLSearchParams): Departure[] {
  const core = query.get('core');
  const origin = query.get('origin');
  const destination = query.get('destination');
  if (core === null && origin === null && destination === null) {
    return bookable(user, query.get('building'), query.get('elevator'));
  }
  if (core === null || origin === null || destination === null) {
    throw new ApiError(400, 'core, origin y destination van los tres juntos o ninguno');
  }
  return offer(user, core, Number(origin), Number(destination));
}

const CAMPUS_TZ = 'America/Argentina/Buenos_Aires';
const campusDay = (at: number | string) =>
  new Date(at).toLocaleDateString('en-CA', { timeZone: CAMPUS_TZ });
const isToday = (at: string) => campusDay(at) === campusDay(Date.now());

/** "dd/MM a las HH:mm" en hora de Buenos Aires, como el mensaje del servidor. */
function formatSuspensionEnd(at: number) {
  const part = Object.fromEntries(
    new Intl.DateTimeFormat('es-AR', {
      timeZone: CAMPUS_TZ,
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(at)
      .map((p) => [p.type, p.value]),
  );
  return `${part.day}/${part.month} a las ${part.hour}:${part.minute}`;
}

/**
 * Fin de la última suspensión (puede ser futuro). Arranca en el pasado, entre dos faltas del
 * historial de ejemplo: cuentan 2 de 3 y la próxima suspende. Las anteriores no cuentan
 * ("cero después de la suspensión", turnos.md §5).
 */
let suspensionEnd = Date.now() - 3.5 * 86_400_000;

/** Faltas que cuentan para la próxima suspensión (todas las de 7 días si ya está suspendido). */
function countedFaults() {
  const windowStart = Date.now() - NO_SHOW_RULE.windowDays * 86_400_000;
  const since = suspensionEnd > Date.now() ? windowStart : Math.max(windowStart, suspensionEnd);
  // Falta = NO_SHOW o CANCELLED_LATE, fechada a la hora de salida (turnos.md §5).
  return history().filter(
    (t) =>
      (t.status === 'NO_SHOW' || t.status === 'CANCELLED_LATE') &&
      Date.parse(t.departureAt) > since,
  );
}

/** Tras una falta nueva: al llegar al umbral suspende 24 h desde esa falta (no a prioritarios). */
function checkSuspension(user: User) {
  if (user.priority !== 'NONE' || suspensionEnd > Date.now()) return;
  const faults = countedFaults();
  if (faults.length >= NO_SHOW_RULE.threshold) {
    const last = Math.max(...faults.map((t) => Date.parse(t.departureAt)));
    suspensionEnd = last + NO_SHOW_RULE.suspensionHours * 3_600_000;
  }
}

const rejected = (status: number, code: string, message: string) =>
  new ApiError(status, message, [], undefined, code);

/** `POST /reservations`, con los rechazos y los `code` del backend (ReservationService). */
function book(user: User, { departureId, originFloor, destinationFloor }: ReserveRequest) {
  const e = Number.isInteger(departureId) ? ELEVATORS[departureId % 100] : undefined;
  if (!e) throw rejected(404, 'DEPARTURE_NOT_FOUND', 'La salida no existe.');
  // Los prioritarios no se suspenden: se evalúa con la prioridad vigente al reservar.
  if (user.priority === 'NONE' && suspensionEnd > Date.now()) {
    throw rejected(
      409,
      'NO_SHOW_SUSPENDED',
      `Por tus faltas de los últimos días no podés reservar hasta el ${formatSuspensionEnd(suspensionEnd)}.`,
    );
  }
  if (outOfService.has(e.code)) {
    throw rejected(422, 'ELEVATOR_NOT_BOOKABLE', 'Esa salida no se puede reservar.');
  }
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
  // El tope diario cuenta todo menos CANCELLED: una cancelación sin falta devuelve el cupo.
  const today = reservations.filter((r) => r.status !== 'CANCELLED' && isToday(r.createdAt));
  if (today.length >= MAX_DAILY_BOOKINGS) {
    throw rejected(409, 'DAILY_LIMIT_REACHED', 'Llegaste al tope de reservas de hoy.');
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
    checkInOpensAt: checkInOpensAt(departure.departureAt),
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

// Arranca con un turno activo en el ascensor 33 (Independencia 2), PB → 7, a unos 16 min.
{
  // A más de 10 min: cancelarlo todavía es sin falta.
  const elevatorIndex = ELEVATORS.findIndex((e) => e.code === '33');
  let at = firstSlot() + 8 * STEP_MS;
  while (occupied(departureIdOf(at, elevatorIndex)) >= CAPACITY) at += STEP_MS;
  const departureId = departureIdOf(at, elevatorIndex);
  reservations = [
    {
      id: ++reservationCounter,
      departureId,
      ...placeOf('33'),
      departureAt: new Date(at).toISOString(),
      cancelFreeUntil: cancelFreeUntil(new Date(at).toISOString()),
      checkInOpensAt: checkInOpensAt(new Date(at).toISOString()),
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
    checkInOpensAt: checkInOpensAt(departsAt.toISOString()),
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
 * "TARDE" fuera de hora. Cualquier otro token no vacío es del ascensor del turno. "DEMO", "OTRO"
 * y "TARDE" (`forced`) saltean la ventana horaria; el resto la respeta como el servidor.
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
  const forced = ['DEMO', 'OTRO', 'TARDE'].some((t) => normalized.includes(t));
  return { elevator, forced, late: normalized.includes('TARDE') };
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
    () => {
      // Sin cookie (sin sesión) el real responde 401.
      const email = sessionEmail();
      if (!email) throw new ApiError(401, 'Sesión vencida');
      return tokensFor(email);
    },
  ],
  [
    'POST',
    /^\/auth\/logout$/,
    () => {
      sessionStorage.removeItem(SESSION_KEY);
      localStorage.removeItem(SESSION_KEY);
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
      if (token.includes('VENCIDO')) {
        throw rejected(400, 'TOKEN_EXPIRED', 'El link venció. Pedí uno nuevo.');
      }
      if (token.includes('USADO')) throw rejected(400, 'TOKEN_USED', 'Este link ya se usó.');
      if (!token) throw rejected(400, 'TOKEN_INVALID', 'El link no es válido.');
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
      if (!token) throw rejected(400, 'TOKEN_INVALID', 'El link no es válido.');
      if (token.toUpperCase().includes('VENCIDO')) {
        throw rejected(400, 'TOKEN_EXPIRED', 'El link venció. Pedí uno nuevo.');
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
  [
    'GET',
    /^\/elevators$/,
    () => ELEVATORS.map((e) => ({ ...e, active: !outOfService.has(e.code) })),
  ],
  ['GET', /^\/congestion$/, () => congestion()],
  ['GET', /^\/admin\/shifts$/, () => SHIFTS],
  ['GET', /^\/admin\/kpis$/, (_, __, query) => adminKpis(query)],
  ['GET', /^\/admin\/elevators$/, () => adminElevators()],
  ['POST', /^\/admin\/elevators\/([^/]+)\/out-of-service$/, (_, [code]) => setService(code, false)],
  ['POST', /^\/admin\/elevators\/([^/]+)\/in-service$/, (_, [code]) => setService(code, true)],
  ['GET', /^\/departures$/, (_, __, query) => departuresFor(currentUser(), query)],
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
      // Idempotente con una ya cancelada; usada y no-show no se cancelan (turnos.md §4).
      if (found.status === 'CANCELLED' || found.status === 'CANCELLED_LATE') return;
      if (found.status === 'USED') {
        throw rejected(
          409,
          'ALREADY_CHECKED_IN',
          'Ya hiciste el check-in: la reserva no se puede cancelar.',
        );
      }
      const departure = Date.parse(found.departureAt);
      if (found.status === 'NO_SHOW' || Date.now() >= departure) {
        throw rejected(
          409,
          'DEPARTURE_ALREADY_PASSED',
          'La salida ya pasó: la reserva no se puede cancelar.',
        );
      }
      const late = departure - Date.now() < CANCEL_DEADLINE_MS;
      reservations = reservations.map((r) =>
        r === found ? { ...r, status: late ? 'CANCELLED_LATE' : 'CANCELLED', seatNumber: null } : r,
      );
      if (late) checkSuspension(currentUser());
    },
  ],
  [
    'POST',
    /^\/check-ins$/,
    (body) => {
      const active = activeReservation();
      const { token } = body as CheckInRequest;
      const demoScenario =
        token === 'DEMO' && new URLSearchParams(window.location.search).get('checkin');
      const scan = scanned(demoScenario || token, active?.elevator ?? '33');
      const { elevator } = scan;
      let { late } = scan;
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
      if (!scan.forced) {
        // Ventana de check-in: −1 min a +2 min de la salida; a tiempo hasta +60 s inclusive.
        const sinceDeparture = Date.now() - Date.parse(active.departureAt);
        if (sinceDeparture < -CHECK_IN_OPENS_MS || sinceDeparture > CHECK_IN_CLOSES_MS) {
          throw rejected(
            409,
            'OUTSIDE_CHECK_IN_WINDOW',
            'Tu turno no está en horario de check-in.',
          );
        }
        late = sinceDeparture > NO_SHOW_AFTER_MS;
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
        source: 'REAL',
      };
      lastCheckedIn = active.id;
      reservations = reservations.map((r) =>
        r === active ? { ...r, status, checkInOutcome: outcome } : r,
      );
      if (late) checkSuspension(currentUser());
      return result;
    },
  ],
  [
    'GET',
    /^\/me\/no-shows$/,
    () => {
      return {
        ...NO_SHOW_RULE,
        recentNoShows: countedFaults().length,
        suspendedUntil: suspensionEnd > Date.now() ? new Date(suspensionEnd).toISOString() : null,
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
  // Vence en el próximo cambio de turno.
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
      const expiresAt = nextQrShift();
      return {
        token: `mock-qr-${elevatorId}-${expiresAt}`,
        expiresAt: new Date(expiresAt).toISOString(),
        elevatorId,
      } satisfies QrToken;
    },
  ],
  ['GET', /^\/admin\/priority-users$/, () => priorityUsers],
  [
    'GET',
    /^\/admin\/users$/,
    (_, __, query) => {
      const q = (query.get('query') ?? '').trim().toLowerCase();
      if (q.length < 3) throw new ApiError(400, 'La búsqueda necesita al menos 3 caracteres');
      const items = adminUsers.filter((u) =>
        [u.fullName, u.email, u.legajo].some((field) => field.toLowerCase().includes(q)),
      );
      return { items, nextCursor: null } satisfies AdminUserPage;
    },
  ],
  [
    'PUT',
    /^\/admin\/users\/([^/]+)\/role$/,
    (body, [id]) => {
      if (id === currentUser().id) throw new ApiError(409, 'No podés cambiar tu propio rol');
      const target = adminUsers.find((u) => u.id === id);
      if (!target) throw new ApiError(404, 'Usuario inexistente');
      target.role = (body as { role: Role }).role;
      return undefined;
    },
  ],
  [
    'PUT',
    /^\/admin\/users\/([^/]+)\/declared-type$/,
    (body, [id]) => {
      const target = adminUsers.find((u) => u.id === id);
      if (!target) throw new ApiError(404, 'Usuario inexistente');
      const type = (body as { declaredUserType: DeclaredUserType }).declaredUserType;
      if (!target.emailVerified) {
        throw new ApiError(
          409,
          'El usuario todavía no verificó su email: no se le puede cambiar el tipo',
        );
      }
      if (target.declaredUserType === type) {
        throw new ApiError(409, `El usuario ya está declarado como ${type}`);
      }
      target.declaredUserType = type;
      const { fullName, email, legajo } = target;
      // Como el servidor: a docente, prioridad directa (sin pasar por la cola); a estudiante, se la quita.
      pendingTeachers = pendingTeachers.filter((t) => t.id !== id);
      priorityUsers = priorityUsers.filter((u) => !(u.id === id && u.category === 'TEACHER'));
      if (type === 'TEACHER') {
        priorityUsers = [...priorityUsers, { id, fullName, email, legajo, category: 'TEACHER' }];
      }
      return undefined;
    },
  ],
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
