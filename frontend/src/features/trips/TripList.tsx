import { Chip, type ChipTone } from '../../components/Chip';
import type { Reservation, ReservationStatus } from '../../types/api';
import { formatDate, formatFloor, formatTime } from '../turn/format';

type Result = { label: string; tone: ChipTone };

const byStatus: Record<ReservationStatus, Result> = {
  ACTIVE: { label: 'Reservado', tone: 'accent' },
  USED: { label: 'Cumplido', tone: 'accent' },
  CANCELLED: { label: 'Cancelado', tone: 'neutral' },
  // Las faltas (turnos.md §5) van en tono de alerta y lo dice el texto.
  CANCELLED_LATE: { label: 'Cancelado tarde (falta)', tone: 'danger' },
  NO_SHOW: { label: 'Falta', tone: 'danger' },
};

/** Estado de la reserva, afinado con el resultado del check-in (check-in-qr.md#resultados). */
function resultOf({ status, checkInOutcome }: Reservation): Result {
  if (status === 'USED' && checkInOutcome === 'OTHER_ELEVATOR') {
    return { label: 'Otro ascensor', tone: 'neutral' };
  }
  if (status === 'NO_SHOW' && checkInOutcome === 'LATE') {
    return { label: 'Falta (fuera de hora)', tone: 'danger' };
  }
  return byStatus[status];
}

/**
 * Agrupa por día en hora de Buenos Aires. Los viajes vienen del más reciente al más viejo,
 * así que alcanza con cortar cuando cambia el día.
 */
function groupByDay(trips: Reservation[]) {
  const days: { day: string; trips: Reservation[] }[] = [];
  for (const trip of trips) {
    const day = formatDate(new Date(trip.departureAt));
    const last = days.at(-1);
    if (last?.day === day) last.trips.push(trip);
    else days.push({ day, trips: [trip] });
  }
  return days;
}

export function TripList({ trips }: { trips: Reservation[] }) {
  return groupByDay(trips).map(({ day, trips }) => (
    <section key={trips[0].id} className="trips__day" aria-labelledby={`day-${trips[0].id}`}>
      <h2 id={`day-${trips[0].id}`} className="trips__day-title">
        {day}
      </h2>
      <ul className="trips__list">
        {trips.map((trip) => {
          const result = resultOf(trip);
          return (
            <li key={trip.id} className="trips__row">
              <span className="trips__slot">{formatTime(trip.departureAt)} hs</span>
              <Chip tone={result.tone}>{result.label}</Chip>
              <span className="trips__detail">
                {trip.coreName} · {formatFloor(trip.originFloor)}
                <span aria-hidden="true"> → </span>
                <span className="visually-hidden"> a </span>
                {formatFloor(trip.destinationFloor)}
              </span>
            </li>
          );
        })}
      </ul>
    </section>
  ));
}
