import { Link, Navigate, useLocation } from 'react-router';
import { OnTimeCard } from '../features/check-in/OnTimeCard';
import { WaitFeedback } from '../features/check-in/WaitFeedback';
import '../features/check-in/checkin.css';
import { formatTime } from '../features/turn/format';
import { SuccessHero } from '../features/turn/SuccessHero';
import '../features/turn/turn.css';
import type { CheckInResult } from '../types/pending';

// El resultado llega por `navigate('/check-in/ok', { state: result })` desde el check-in.
const isResult = (state: unknown): state is CheckInResult =>
  typeof state === 'object' &&
  state !== null &&
  typeof (state as CheckInResult).reservationId === 'string' &&
  ['ON_TIME', 'OTHER_ELEVATOR', 'LATE'].includes((state as CheckInResult).outcome);

// El hero cierra el flujo: con LATE dice la consecuencia en vez de mostrar éxito (#149).
const heroes = {
  ON_TIME: { title: 'Viaje registrado', tone: 'success' },
  OTHER_ELEVATOR: { title: 'Viaje registrado en otro ascensor', tone: 'success' },
  LATE: { title: 'Llegaste fuera de tu franja: cuenta como falta', tone: 'danger' },
} as const;

export function CheckInSuccessPage() {
  const { state } = useLocation();
  // Entrada directa o recarga: no hay check-in reciente que mostrar.
  if (!isResult(state)) return <Navigate to="/" replace />;

  const hero = heroes[state.outcome];
  return (
    <div className="success-screen success-screen--check-in">
      <SuccessHero title={hero.title} tone={hero.tone}>
        {state.elevatorName} · {state.coreName} · {formatTime(state.checkedInAt)} hs
      </SuccessHero>
      <OnTimeCard result={state} />
      <WaitFeedback reservationId={state.reservationId} />
      <div className="turn-actions">
        <Link to="/" className="button button--primary">
          Volver al inicio
        </Link>
      </div>
    </div>
  );
}
