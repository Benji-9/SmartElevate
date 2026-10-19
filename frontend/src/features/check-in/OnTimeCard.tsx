import type { CheckIn, CheckInOutcome } from '../../types/api';
import { formatTime } from '../turn/format';

const titles: Record<CheckInOutcome, string> = {
  ON_TIME: 'Check-in a tiempo',
  OTHER_ELEVATOR: 'Check-in en otro ascensor',
  LATE: 'Check-in fuera de hora',
};

function detail({ outcome, elevator, reservedElevator }: CheckIn) {
  if (outcome === 'ON_TIME')
    return 'Subiste dentro de tu franja. ¡Gracias por ayudar a que el ascensor fluya!';
  if (outcome === 'OTHER_ELEVATOR') {
    return `Reservaste el ascensor ${reservedElevator} y escaneaste el ${elevator}. Quedó registrado y no cuenta como falta.`;
  }
  return 'El check-in fue después de la hora de tu salida, así que cuenta como falta. Igual queda registrado que viniste.';
}

/** Resultado del check-in (docs/reglas/check-in-qr.md#resultados) con la salida del turno. */
export function OnTimeCard({ result }: { result: CheckIn }) {
  return (
    <section
      className={`checkin-card checkin-card--${result.outcome.toLowerCase()}`}
      aria-labelledby="checkin-card-title"
    >
      <h2 id="checkin-card-title" className="checkin-card__title text-body-strong">
        {titles[result.outcome]}
      </h2>
      <p>{detail(result)}</p>
      <p>
        Salida de tu turno: <strong>{formatTime(result.departureAt)} hs</strong>
      </p>
    </section>
  );
}
