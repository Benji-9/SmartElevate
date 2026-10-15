import type { Reservation } from '../../types/api';
import { formatFloor, formatTime } from './format';

/**
 * Detalle de una reserva: edificio, ascensor, recorrido y salida. La franja es la salida
 * (ADR 0007): la API da su hora, no una duración.
 */
export function TurnDetails({ reservation }: { reservation: Reservation }) {
  const { buildingName, coreName, elevator, departureAt, originFloor, destinationFloor } =
    reservation;

  return (
    <dl className="turn-details">
      <div>
        <dt>Edificio</dt>
        <dd>{buildingName}</dd>
      </div>
      <div>
        <dt>Ascensor</dt>
        <dd>
          {elevator}
          <span className="turn-details__muted"> · {coreName}</span>
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
        <dt>Salida</dt>
        <dd>{formatTime(departureAt)} hs</dd>
      </div>
    </dl>
  );
}
