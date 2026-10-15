import type { Building, Departure, Elevator } from '../../types/api';

/**
 * Núcleo para elegir en Reservar. El contrato modela por ascensor: el núcleo lo arma el front
 * con `GET /buildings` (nombre, rango de pisos) y `GET /elevators` (pisos de cada ascensor).
 */
export type CoreOption = { code: string; name: string; building: Building; elevators: Elevator[] };

/** Núcleos con algún ascensor activo, en el orden de los edificios. */
export function coreOptions(buildings: Building[], elevators: Elevator[]): CoreOption[] {
  return buildings.flatMap((building) =>
    building.cores.flatMap((core) => {
      const own = elevators.filter((e) => e.active && e.core === core.code);
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

/** Una franja: la hora de salida y la salida que se reserva en ella. */
export type Slot = { departureAt: string; departure: Departure };

/**
 * Agrupa por hora las salidas de los ascensores dados (todos los de un núcleo salen a la misma
 * hora). En cada hora se ofrece la salida con más lugares libres; a igualdad, la primera que
 * manda el servidor (ordenadas por ascensor).
 */
export function slotsOf(departures: Departure[], elevators: Elevator[]): Slot[] {
  const codes = new Set(elevators.map((e) => e.code));
  const byTime = new Map<string, Departure>();
  for (const d of departures) {
    if (d.status !== 'SCHEDULED' || !codes.has(d.elevator)) continue;
    const best = byTime.get(d.departureAt);
    if (!best || d.remainingSeats > best.remainingSeats) byTime.set(d.departureAt, d);
  }
  return [...byTime.entries()]
    .map(([departureAt, departure]) => ({ departureAt, departure }))
    .sort((a, b) => Date.parse(a.departureAt) - Date.parse(b.departureAt));
}
