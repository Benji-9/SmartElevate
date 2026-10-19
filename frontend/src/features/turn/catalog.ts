import type { Building, Departure, Elevator, PriorityCategory } from '../../types/api';

/**
 * Núcleo para elegir en Reservar. El contrato modela por ascensor: el núcleo lo arma el front
 * con `GET /buildings` (nombre, rango de pisos) y `GET /elevators` (pisos de cada ascensor).
 */
export type CoreOption = { code: string; name: string; building: Building; elevators: Elevator[] };

/**
 * Si el usuario puede usar el ascensor: los comunes, cualquiera; un dedicado, solo quien tiene esa prioridad
 * vigente. Es el espejo de `ElevatorUsage.allows` del backend, que igual lo exige al listar salidas y reservar.
 */
export const canUse = (elevator: Elevator, priority: PriorityCategory) =>
  elevator.usage === 'COMMON' ||
  (elevator.usage === 'TEACHERS' && priority === 'TEACHER') ||
  (elevator.usage === 'REDUCED_MOBILITY' && priority === 'REDUCED_MOBILITY');

/**
 * Núcleos con algún ascensor activo que el usuario pueda usar, en el orden de los edificios. Un núcleo
 * donde todo es dedicado a otra categoría (p. ej. Independencia 1 para un usuario común) no se ofrece.
 */
// ponytail: el orden de los edificios es el de la spec (SCREENS.md §04, Lima primero); el contrato los
// manda por código. Si suma un orden propio, usar ese.
const BUILDING_ORDER = ['LIMA', 'INDEPENDENCIA'];
const rank = (code: string) => {
  const i = BUILDING_ORDER.indexOf(code);
  return i < 0 ? BUILDING_ORDER.length : i;
};

export function coreOptions(
  buildings: Building[],
  elevators: Elevator[],
  priority: PriorityCategory,
): CoreOption[] {
  return [...buildings]
    .sort((a, b) => rank(a.code) - rank(b.code))
    .flatMap((building) =>
      building.cores.flatMap((core) => {
        const own = elevators.filter(
          (e) => e.active && e.core === core.code && canUse(e, priority),
        );
        return own.length ? [{ ...core, building, elevators: own }] : [];
      }),
    );
}

const inRange = (building: Building) => (floor: number) =>
  floor >= building.minFloor && floor <= building.maxFloor;

/** Pisos donde para algún ascensor del núcleo (dentro del edificio), de menor a mayor. */
export const originFloorsOf = (core: CoreOption | undefined) =>
  core
    ? [...new Set(core.elevators.flatMap((e) => e.floors))]
        .filter(inRange(core.building))
        .sort((a, b) => a - b)
    : [];

/** Ascensores del núcleo que paran en todos los pisos dados (cada batería sirve los suyos). */
export const elevatorsServing = (core: CoreOption, floors: number[]) =>
  core.elevators.filter((e) => floors.every((f) => e.floors.includes(f)));

/**
 * Destinos posibles desde `origin`: los pisos de los ascensores que paran en el origen. La regla de
 * pisos bajos no se aplica acá: la evalúa el servidor al reservar (LOW_FLOOR_DESTINATION).
 */
export const destinationFloorsOf = (core: CoreOption, origin: number) =>
  originFloorsOf({ ...core, elevators: elevatorsServing(core, [origin]) }).filter(
    (f) => f !== origin,
  );

/**
 * Si el piso de destino va por escalera: del 1 al umbral de pisos bajos (`low_floors_threshold`, lo manda
 * `GET /buildings`), salvo movilidad reducida. Es el espejo de `ReservationService.checkFloors`, que igual lo exige.
 */
export const isLowFloor = (building: Building, floor: number, priority: PriorityCategory) =>
  priority !== 'REDUCED_MOBILITY' && floor >= 1 && floor <= building.lowFloorsThreshold;

/** Una franja: la hora de salida y la salida que se reserva en ella. */
export type Slot = { departureAt: string; departure: Departure };

/**
 * Agrupa por hora las salidas de los ascensores dados (todos los de un núcleo salen a la misma
 * hora). En cada hora se ofrece la salida con más lugares libres; a igualdad, la primera que
 * manda el servidor (ordenadas por ascensor).
 */
export function slotsOf(departures: Departure[], elevators: Elevator[], now: number): Slot[] {
  const codes = new Set(elevators.map((e) => e.code));
  const byTime = new Map<string, Departure>();
  for (const d of departures) {
    // La ventana de reserva cerró: el servidor la rechazaría (OUTSIDE_BOOKING_WINDOW).
    if (d.status !== 'SCHEDULED' || !codes.has(d.elevator) || Date.parse(d.bookingClosesAt) <= now)
      continue;
    const best = byTime.get(d.departureAt);
    if (!best || d.remainingSeats > best.remainingSeats) byTime.set(d.departureAt, d);
  }
  return [...byTime.entries()]
    .map(([departureAt, departure]) => ({ departureAt, departure }))
    .sort((a, b) => Date.parse(a.departureAt) - Date.parse(b.departureAt));
}
