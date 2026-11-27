import { Link, Navigate, useLocation } from 'react-router';
import { OnTimeCard } from '../features/check-in/OnTimeCard';
import '../features/check-in/checkin.css';
import { formatTime } from '../features/turn/format';
import { SuccessHero } from '../features/turn/SuccessHero';
import '../features/turn/turn.css';
import { NoShowNotice } from '../features/trips/NoShowNotice';
import { useResource } from '../hooks/useResource';
import { getNoShowStatus } from '../services/api';
import type { CheckIn } from '../types/api';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

// El resultado llega por `navigate('/check-in/ok', { state: result })` desde el check-in.
const isResult = (state: unknown): state is CheckIn =>
  typeof state === 'object' &&
  state !== null &&
  typeof (state as CheckIn).elevator === 'string' &&
  ['ON_TIME', 'OTHER_ELEVATOR', 'LATE'].includes((state as CheckIn).outcome);

// El hero cierra el flujo: con LATE dice la consecuencia en vez de mostrar éxito (#149).
const heroes = {
  ON_TIME: { title: 'Viaje registrado', tone: 'success' },
  OTHER_ELEVATOR: { title: 'Viaje registrado en otro ascensor', tone: 'success' },
  LATE: { title: 'Llegaste tarde: se registró como falta', tone: 'danger' },
} as const;
/** Una llegada tarde con check-in simulado no es falta (turnos.md §5). */
const simulatedLate = {
  title: 'Llegaste tarde: check-in simulado, sin falta',
  tone: 'danger',
} as const;

/** Qué sigue después de una falta: conteo y umbral salen del servidor. */
function LateNoShows() {
  const { data, error, loading } = useResource(getNoShowStatus);
  if (loading) {
    return (
      <p role="status" className="page-placeholder">
        Cargando tus faltas…
      </p>
    );
  }
  if (error) {
    return (
      <p role="alert" className="turn-alert">
        {error}
      </p>
    );
  }
  return data ? <NoShowNotice status={data} /> : null;
}

/**
 * 07 · Viaje registrado. La encuesta de espera queda afuera hasta que el backend tenga dónde
 * guardarla (SCREENS.md §07).
 */
export function CheckInSuccessPage() {
  useDocumentTitle('Check-in registrado');
  const { state } = useLocation();
  // Entrada directa o recarga: no hay check-in reciente que mostrar.
  if (!isResult(state)) return <Navigate to="/" replace />;

  const late = state.outcome === 'LATE';
  const simulated = state.source === 'SIMULATED';
  const hero = late && simulated ? simulatedLate : heroes[state.outcome];
  return (
    <div className="success-screen success-screen--check-in">
      <SuccessHero title={hero.title} tone={hero.tone} pose="festejo">
        Ascensor {state.elevator} · {formatTime(state.checkedInAt)} hs
      </SuccessHero>
      <OnTimeCard result={state} />
      {late && !simulated && <LateNoShows />}
      <div className="turn-actions">
        <Link to="/" className="button button--primary">
          Volver al inicio
        </Link>
      </div>
    </div>
  );
}
