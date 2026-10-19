import { useEffect, useState } from 'react';
import { Link } from 'react-router';
// Button trae los estilos `.button`, que también usan los links con forma de botón.
import { Button } from '../../components/Button';
import { Chip } from '../../components/Chip';
import { Mascot } from '../../components/Mascot';
import { useNow } from '../../hooks/useNow';
import { ApiError, getActiveReservation } from '../../services/api';
import type { Reservation } from '../../types/api';
import { QR_INSTRUCTION } from '../check-in/qrInstruction';
import { formatFloor, formatTime } from './format';
import './ActiveTurnCard.css';
import { Skeleton } from '../../components/Skeleton';

type State =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; reservation: Reservation | null };

/**
 * "Sale en 6 min": tiempo hasta la salida reservada, a partir del instante UTC de la API
 * (no depende de la zona del dispositivo). Se actualiza cada 30 s mientras está montado.
 */
function DepartureCountdown({ departsAt }: { departsAt: string }) {
  const now = useNow(30_000);
  const minutes = Math.ceil((Date.parse(departsAt) - now) / 60_000);
  return (
    <p className="turn-card__countdown">{minutes > 0 ? `Sale en ${minutes} min` : 'Sale ahora'}</p>
  );
}

/** "Hacer check-in" recién cuando abre; antes, la hora de apertura (#285). */
function CheckInAction({ opensAt }: { opensAt: string }) {
  const now = useNow(5_000);
  if (now >= Date.parse(opensAt)) {
    return (
      <Link to="/check-in" className="button button--primary button--inline">
        Hacer check-in
      </Link>
    );
  }
  return <p className="turn-card__help">Abre a las {formatTime(opensAt)} hs</p>;
}

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
    return <Skeleton label="Cargando tu turno…" height={168} className="skeleton--card" />;
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
        <Mascot size={96} className="turn-card__mascot" />
        <h2 id="turn-card-title" className="text-card-title">
          No tenés un turno activo
        </h2>
        <p className="turn-card__help">
          Elevy te ayuda: reservá un lugar en la próxima salida del ascensor.
        </p>
        <Link to="/reservar" className="button button--primary button--inline">
          Reservar turno
        </Link>
      </section>
    );
  }

  return (
    <section className="turn-card" aria-labelledby="turn-card-title">
      <div className="turn-card__header">
        <h2 id="turn-card-title" className="text-card-title">
          Tu turno
        </h2>
        <Chip>Confirmado</Chip>
      </div>
      <DepartureCountdown departsAt={reservation.departureAt} />
      <p className="turn-card__summary">
        {formatTime(reservation.departureAt)} hs · {reservation.coreName} · Ascensor{' '}
        {reservation.elevator} · Piso {formatFloor(reservation.destinationFloor)}
      </p>
      <p className="turn-card__help">{QR_INSTRUCTION}</p>
      <CheckInAction opensAt={reservation.checkInOpensAt} />
      <Link to={`/turno/${encodeURIComponent(reservation.id)}`} className="turn-card__link">
        Ver turno
      </Link>
    </section>
  );
}
