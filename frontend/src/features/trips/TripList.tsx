import { Chip } from '../../components/Chip';
import type { Trip, TripResult } from '../../types/pending';
import { formatDate, formatFloor, formatSlot } from '../turn/format';

const results: Record<TripResult, { label: string; tone: 'primary' | 'neutral' | 'danger' }> = {
  COMPLETED: { label: 'Cumplido', tone: 'primary' },
  OTHER_ELEVATOR: { label: 'Otro ascensor', tone: 'neutral' },
  LATE: { label: 'Fuera de hora', tone: 'neutral' },
  CANCELLED: { label: 'Cancelado', tone: 'neutral' },
  NO_SHOW: { label: 'Falta', tone: 'danger' },
};

/**
 * Agrupa por día en hora de Buenos Aires. Los viajes vienen del más reciente al más viejo,
 * así que alcanza con cortar cuando cambia el día.
 */
function groupByDay(trips: Trip[]) {
  const days: { day: string; trips: Trip[] }[] = [];
  for (const trip of trips) {
    const day = formatDate(new Date(trip.departsAt));
    const last = days.at(-1);
    if (last?.day === day) last.trips.push(trip);
    else days.push({ day, trips: [trip] });
  }
  return days;
}

export function TripList({ trips }: { trips: Trip[] }) {
  return groupByDay(trips).map(({ day, trips }) => (
    <section key={trips[0].id} className="trips__day" aria-labelledby={`day-${trips[0].id}`}>
      <h2 id={`day-${trips[0].id}`} className="trips__day-title">
        {day}
      </h2>
      <ul className="trips__list">
        {trips.map((trip) => {
          const result = results[trip.result];
          return (
            <li key={trip.id} className="trips__row">
              <span className="trips__slot">
                {formatSlot(trip.departsAt, trip.durationMinutes)}
              </span>
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
