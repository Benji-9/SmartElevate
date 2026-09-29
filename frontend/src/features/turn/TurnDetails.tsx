import type { Reservation } from '../../types/pending';
import { formatFloor, formatSlot } from './format';

/** Detalle de una reserva: edificio, ascensor, recorrido, franja y ocupación. */
export function TurnDetails({ reservation }: { reservation: Reservation }) {
  const { core, departure, originFloor, destinationFloor } = reservation;
  const floors = core.floors.length
    ? `pisos ${formatFloor(Math.min(...core.floors))} a ${formatFloor(Math.max(...core.floors))}`
    : null;

  return (
    <dl className="turn-details">
      <div>
        <dt>Edificio</dt>
        <dd>{core.buildingName}</dd>
      </div>
      <div>
        <dt>Ascensor</dt>
        <dd>
          {core.name}
          {floors && <span className="turn-details__muted"> · {floors}</span>}
        </dd>
      </div>
      <div>
        <dt>Recorrido</dt>
        <dd>
          {formatFloor(originFloor)} <span aria-hidden="true">→</span>
          <span className="visually-hidden">a</span> {formatFloor(destinationFloor)}
        </dd>
      </div>
      <div>
        <dt>Franja</dt>
        <dd>{formatSlot(departure.departsAt, departure.durationMinutes)}</dd>
      </div>
      <div>
        <dt>Ocupación</dt>
        <dd>
          {departure.occupied} / {departure.capacity} personas
        </dd>
      </div>
    </dl>
  );
}
