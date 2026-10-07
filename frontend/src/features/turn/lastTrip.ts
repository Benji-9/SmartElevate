const KEY = 'se:lastTrip';

/**
 * Núcleo y pisos del último turno reservado en este dispositivo, para precargar Reservar.
 * La franja no se guarda: depende del cupo del momento. El historial de la API no trae el id
 * del núcleo, por eso va en `localStorage`.
 */
export type LastTrip = { coreId: string; originFloor: number; destinationFloor: number };

export function loadLastTrip(): LastTrip | null {
  try {
    const trip = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Partial<LastTrip> | null;
    return typeof trip?.coreId === 'string' &&
      Number.isInteger(trip.originFloor) &&
      Number.isInteger(trip.destinationFloor)
      ? (trip as LastTrip)
      : null;
  } catch {
    // Sin localStorage (bloqueado o modo privado) o un valor roto: el formulario abre vacío.
    return null;
  }
}

export function saveLastTrip(trip: LastTrip) {
  try {
    localStorage.setItem(KEY, JSON.stringify(trip));
  } catch {
    // Sin localStorage: la próxima vez no se precarga, nada más.
  }
}
