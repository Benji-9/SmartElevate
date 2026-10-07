import type { CheckInOutcome, CheckInResult } from '../../types/pending';
import { formatSlot } from '../turn/format';

const outcomes: Record<CheckInOutcome, { title: string; detail: string }> = {
  ON_TIME: { title: 'Llegás a tiempo', detail: 'Subiste dentro de tu franja.' },
  OTHER_ELEVATOR: {
    title: 'Check-in en otro ascensor',
    detail: 'Subiste a un ascensor distinto al de tu turno. Igual quedó registrado.',
  },
  LATE: {
    title: 'Check-in fuera de hora',
    detail: 'Llegaste pasada tu franja: cuenta como falta, pero queda registrado que viniste.',
  },
};

/** Resultado del check-in (docs/reglas/check-in-qr.md#resultados) con la franja del turno. */
export function OnTimeCard({ result }: { result: CheckInResult }) {
  const { title, detail } = outcomes[result.outcome];
  return (
    <section
      className={`checkin-card checkin-card--${result.outcome.toLowerCase()}`}
      aria-labelledby="checkin-card-title"
    >
      <h2 id="checkin-card-title" className="checkin-card__title text-body-strong">
        {title}
      </h2>
      <p>{detail}</p>
      <p>
        Tu turno: <strong>{formatSlot(result.departsAt, result.durationMinutes)}</strong>
      </p>
    </section>
  );
}
