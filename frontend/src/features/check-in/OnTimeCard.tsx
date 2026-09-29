import { Chip } from '../../components/Chip';
import type { CheckInOutcome, CheckInResult } from '../../types/pending';
import { formatSlot } from '../turn/format';

const outcomes: Record<
  CheckInOutcome,
  { title: string; detail: string; tone: 'primary' | 'neutral' | 'danger' }
> = {
  ON_TIME: { title: 'Check-in a tiempo', detail: 'Llegaste dentro de tu franja.', tone: 'primary' },
  OTHER_ELEVATOR: {
    title: 'Check-in en otro ascensor',
    detail: 'Subiste a un ascensor distinto al de tu turno. Igual quedó registrado.',
    tone: 'neutral',
  },
  LATE: {
    title: 'Check-in fuera de hora',
    detail: 'Llegaste pasada tu franja: cuenta como falta, pero queda registrado que viniste.',
    tone: 'danger',
  },
};

/** Resultado del check-in (docs/reglas/check-in-qr.md#resultados) con la franja del turno. */
export function OnTimeCard({ result }: { result: CheckInResult }) {
  const { title, detail, tone } = outcomes[result.outcome];
  return (
    <section className="checkin-card" aria-labelledby="checkin-card-title">
      <h2 id="checkin-card-title" className="checkin-card__title">
        <Chip tone={tone}>{title}</Chip>
      </h2>
      <p>{detail}</p>
      <p className="checkin-card__slot">
        Tu turno: <strong>{formatSlot(result.departsAt, result.durationMinutes)}</strong>
      </p>
    </section>
  );
}
