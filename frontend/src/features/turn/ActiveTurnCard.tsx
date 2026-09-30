import { useEffect, useState } from 'react';
import { Link } from 'react-router';
// Button trae los estilos `.button`, que también usan los links con forma de botón.
import { Button } from '../../components/Button';
import { Chip } from '../../components/Chip';
import { ApiError, getActiveReservation } from '../../services/api';
import type { Reservation } from '../../types/pending';
import { formatFloor, formatSlot } from './format';
import './ActiveTurnCard.css';

type State =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; reservation: Reservation | null };

/** Tarjeta "Tu turno" del inicio, o el CTA para reservar si no hay turno activo. */
export function ActiveTurnCard() {
  const [state, setState] = useState<State>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    getActiveReservation().then(
      (reservation) => active && setState({ status: 'ready', reservation: reservation ?? null }),
      (error: unknown) =>
        active &&
        setState({
          status: 'error',
          message: error instanceof ApiError ? error.message : 'No pudimos cargar tu turno.',
        }),
    );
    return () => {
      active = false;
    };
  }, [attempt]);

  if (state.status === 'loading') {
    return (
      <p role="status" className="page-placeholder">
        Cargando tu turno…
      </p>
    );
  }

  if (state.status === 'error') {
    return (
      <div role="alert" className="turn-card turn-card--error">
        <p>{state.message}</p>
        <Button
          variant="secondary"
          block={false}
          onClick={() => {
            setState({ status: 'loading' });
            setAttempt((n) => n + 1);
          }}
        >
          Reintentar
        </Button>
      </div>
    );
  }

  const { reservation } = state;
  if (!reservation) {
    return (
      <section className="turn-card" aria-labelledby="turn-card-title">
        <h2 id="turn-card-title" className="text-card-title">
          No tenés un turno activo
        </h2>
        <p className="turn-card__help">Reservá un lugar en la próxima salida del ascensor.</p>
        <Link to="/reservar" className="button button--primary button--inline">
          Reservar turno
        </Link>
      </section>
    );
  }

  const { departure, core } = reservation;
  return (
    <section className="turn-card" aria-labelledby="turn-card-title">
      <div className="turn-card__header">
        <h2 id="turn-card-title" className="text-card-title">
          Tu turno
        </h2>
        <Chip>Confirmado</Chip>
      </div>
      <p className="turn-card__summary">
        {formatSlot(departure.departsAt, departure.durationMinutes)} · {core.name} · Piso{' '}
        {formatFloor(reservation.destinationFloor)}
      </p>
      <p className="turn-card__help">
        Esperá en {core.hall} y escaneá el QR del ascensor para hacer check-in.
      </p>
      <Link to="/check-in" className="button button--primary button--inline">
        Hacer check-in
      </Link>
      <Link to={`/turno/${encodeURIComponent(reservation.id)}`} className="turn-card__link">
        Ver turno
      </Link>
    </section>
  );
}
