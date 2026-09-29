import { Link, Navigate, useLocation } from 'react-router';
import { OnTimeCard } from '../features/check-in/OnTimeCard';
import { WaitFeedback } from '../features/check-in/WaitFeedback';
import '../features/check-in/checkin.css';
import { formatTime } from '../features/turn/format';
import '../features/turn/turn.css';
import type { CheckInResult } from '../types/pending';

// El resultado llega por `navigate('/check-in/ok', { state: result })` desde el check-in.
const isResult = (state: unknown): state is CheckInResult =>
  typeof state === 'object' &&
  state !== null &&
  typeof (state as CheckInResult).reservationId === 'string' &&
  ['ON_TIME', 'OTHER_ELEVATOR', 'LATE'].includes((state as CheckInResult).outcome);

export function CheckInSuccessPage() {
  const { state } = useLocation();
  // Entrada directa o recarga: no hay check-in reciente que mostrar.
  if (!isResult(state)) return <Navigate to="/" replace />;

  return (
    <>
      <section className="turn-hero" aria-labelledby="checkin-hero-title">
        <span className="turn-hero__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="32" height="32">
            <path
              d="M5 12.5l4.5 4.5L19 7.5"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
        <h1 id="checkin-hero-title">Viaje registrado</h1>
        <p className="turn-hero__hall">
          {state.elevatorName} · {state.coreName} · {formatTime(state.checkedInAt)}
        </p>
      </section>
      <OnTimeCard result={state} />
      <WaitFeedback reservationId={state.reservationId} />
      <div className="turn-actions">
        <Link to="/" className="button button--primary">
          Volver al inicio
        </Link>
      </div>
    </>
  );
}
