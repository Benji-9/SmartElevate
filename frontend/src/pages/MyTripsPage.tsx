import { useState } from 'react';
import { Link } from 'react-router';
import { Button } from '../components/Button';
import { ScreenHeader } from '../components/ScreenHeader';
import { NoShowNotice } from '../features/trips/NoShowNotice';
import { TripList } from '../features/trips/TripList';
import '../features/trips/trips.css';
import { errorMessage, useResource } from '../hooks/useResource';
import { getNoShowStatus, getTrips } from '../services/api';
import type { TripPage } from '../types/pending';
import { Skeleton } from '../components/Skeleton';

export function MyTripsPage() {
  const first = useResource(getTrips);
  // El aviso de faltas es secundario: si falla, la lista se muestra igual sin él.
  const noShows = useResource(getNoShowStatus);
  // Páginas pedidas con "Ver más", en orden.
  const [more, setMore] = useState<TripPage[]>([]);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState<string | null>(null);

  const trips = [...(first.data?.items ?? []), ...more.flatMap((page) => page.items)];
  const nextCursor = (more.at(-1) ?? first.data)?.nextCursor ?? null;

  async function loadMore() {
    setLoadingMore(true);
    setMoreError(null);
    try {
      const page = await getTrips(nextCursor);
      setMore((pages) => [...pages, page]);
    } catch (error) {
      setMoreError(errorMessage(error));
    } finally {
      setLoadingMore(false);
    }
  }

  let content;
  if (first.error) {
    content = (
      <div role="alert" className="trips__error">
        <p>{first.error}</p>
        <Button variant="secondary" onClick={first.reload}>
          Reintentar
        </Button>
      </div>
    );
  } else if (first.loading) {
    content = <Skeleton label="Cargando tus viajes…" rows={3} height={72} />;
  } else if (trips.length === 0) {
    content = (
      <p className="page-placeholder">
        Todavía no tenés viajes. <Link to="/reservar">Reservá tu primer turno</Link>.
      </p>
    );
  } else {
    content = (
      <>
        <TripList trips={trips} />
        {moreError && (
          <p role="alert" className="trips__error">
            {moreError}
          </p>
        )}
        {nextCursor && (
          <Button
            variant="secondary"
            onClick={loadMore}
            loading={loadingMore}
            loadingLabel="Cargando más viajes…"
          >
            Ver más
          </Button>
        )}
      </>
    );
  }

  return (
    <>
      <ScreenHeader title="Mis viajes" backTo="/perfil" />
      {noShows.data && <NoShowNotice status={noShows.data} />}
      {content}
    </>
  );
}
