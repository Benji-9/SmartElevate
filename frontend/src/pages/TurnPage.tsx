import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { Button } from '../components/Button';
import { Chip } from '../components/Chip';
import { ScreenHeader } from '../components/ScreenHeader';
import { CancelTurnDialog } from '../features/turn/CancelTurnDialog';
import { CheckinHint } from '../features/turn/CheckinHint';
import { SuccessHero } from '../features/turn/SuccessHero';
import { TurnDetails } from '../features/turn/TurnDetails';
import '../features/turn/turn.css';
import { ApiError, cancelReservation, getReservation } from '../services/api';
import type { Reservation, ReservationStatus } from '../types/pending';

const errorMessage = (error: unknown) =>
  error instanceof ApiError ? error.message : 'No pudimos conectarnos. Probá de nuevo.';

const closedStatus: Record<Exclude<ReservationStatus, 'ACTIVE'>, string> = {
  COMPLETED: 'Viaje cumplido',
  CANCELLED: 'Turno cancelado',
  NO_SHOW: 'Falta',
};

export function TurnPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  // Resultado de la carga, atado al `id` pedido: si cambia la ruta, vuelve a "cargando".
  const [loaded, setLoaded] = useState<{
    id: string;
    reservation?: Reservation;
    error?: unknown;
  } | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const current = loaded?.id === id ? loaded : null;
  const reservation = current?.reservation;
  const loadError = current?.error;

  useEffect(() => {
    let ignore = false;
    getReservation(id).then(
      (result) => !ignore && setLoaded({ id, reservation: result }),
      (error: unknown) => !ignore && setLoaded({ id, error }),
    );
    return () => {
      ignore = true;
    };
  }, [id]);

  async function confirmCancel() {
    setCancelling(true);
    setCancelError(null);
    try {
      await cancelReservation(id);
      navigate('/', { replace: true, state: { notice: 'Cancelaste tu turno.' } });
    } catch (error) {
      setCancelError(errorMessage(error));
      setCancelling(false);
    }
  }

  function closeDialog() {
    setDialogOpen(false);
    setCancelError(null);
  }

  let content;
  if (loadError instanceof ApiError && loadError.status === 404) {
    content = (
      <section className="turn-hint turn-not-found">
        <h2>No encontramos este turno</h2>
        <p>Puede que se haya cancelado o que no sea tuyo.</p>
        <p>
          <Link to="/">Ir al inicio</Link> · <Link to="/reservar">Reservar un turno</Link>
        </p>
      </section>
    );
  } else if (loadError) {
    content = (
      <p role="alert" className="turn-alert">
        {errorMessage(loadError)}
      </p>
    );
  } else if (!reservation) {
    content = (
      <p role="status" className="page-placeholder">
        Cargando tu turno…
      </p>
    );
  } else if (reservation.status !== 'ACTIVE') {
    content = (
      <>
        <Chip tone={reservation.status === 'NO_SHOW' ? 'danger' : 'neutral'}>
          {closedStatus[reservation.status]}
        </Chip>
        <TurnDetails reservation={reservation} />
      </>
    );
  } else {
    // Turno activo: pantalla de confirmación sin header, con el hero como `h1`.
    return (
      <div className="success-screen">
        <SuccessHero title="¡Turno confirmado!">Esperá en {reservation.core.hall}.</SuccessHero>
        <TurnDetails reservation={reservation} />
        <CheckinHint />
        <div className="turn-actions turn-actions--row">
          <Link to="/check-in" className="button button--primary">
            Ir a check-in
          </Link>
          <Button variant="secondary" onClick={() => setDialogOpen(true)}>
            Cancelar turno
          </Button>
        </div>
        <CancelTurnDialog
          open={dialogOpen}
          countsAsNoShow={reservation.cancelCountsAsNoShow}
          cancelling={cancelling}
          error={cancelError}
          onConfirm={confirmCancel}
          onClose={closeDialog}
        />
      </div>
    );
  }

  return (
    <>
      <ScreenHeader title="Tu turno" />
      {content}
    </>
  );
}
