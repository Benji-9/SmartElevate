import { useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { Button } from '../components/Button';
import { ScreenHeader } from '../components/ScreenHeader';
import { BuildingSelector } from '../features/turn/BuildingSelector';
import { FloorSelector } from '../features/turn/FloorSelector';
import { formatTime } from '../features/turn/format';
import { OriginFloorInput } from '../features/turn/OriginFloorInput';
import { SlotList } from '../features/turn/SlotList';
import '../features/turn/ReserveTurn.css';
import { errorMessage, useResource } from '../hooks/useResource';
import {
  getActiveReservation,
  getBuildings,
  getCores,
  getDepartures,
  getDestinationFloors,
  reserve,
} from '../services/api';

const loadCatalog = () => Promise.all([getBuildings(), getCores()]);
// Si no se puede saber, no se avisa: el servidor rechaza la reserva igual.
const loadActive = () => getActiveReservation().catch(() => null);

export function ReserveTurnPage() {
  const navigate = useNavigate();
  const [coreId, setCoreId] = useState<string | null>(null);
  const [origin, setOrigin] = useState<number | null>(null);
  const [destination, setDestination] = useState<number | null>(null);
  const [departureId, setDepartureId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const catalog = useResource(loadCatalog);
  const active = useResource(loadActive);
  const floors = useResource(
    useMemo(
      () => (coreId && origin !== null ? () => getDestinationFloors(coreId, origin) : null),
      [coreId, origin],
    ),
  );
  const departures = useResource(
    useMemo(() => (coreId ? () => getDepartures(coreId) : null), [coreId]),
  );

  const [buildings, cores] = catalog.data ?? [[], []];
  const core = cores.find((c) => c.id === coreId);
  const building = buildings.find((b) => b.id === core?.buildingId);
  const originFloors =
    core && building
      ? core.floors.filter((f) => f >= building.minFloor && f <= building.maxFloor)
      : [];
  // Si la elección dejó de ser válida (p. ej. la salida se llenó al recargar), no cuenta.
  const validDestination = floors.data?.some((f) => f.floor === destination && f.eligible)
    ? destination
    : null;
  const departure = departures.data?.find((d) => d.id === departureId && d.occupied < d.capacity);
  const ready = origin !== null && validDestination !== null && departure !== undefined;

  function chooseCore(id: string) {
    setCoreId(id);
    setOrigin(null);
    setDestination(null);
    setDepartureId(null);
    setSubmitError(null);
  }

  function chooseOrigin(floor: number) {
    setOrigin(floor);
    setDestination(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (origin === null || validDestination === null || !departure) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const reservation = await reserve({
        departureId: departure.id,
        originFloor: origin,
        destinationFloor: validDestination,
      });
      navigate(`/turno/${encodeURIComponent(reservation.id)}`);
    } catch (error) {
      setSubmitError(errorMessage(error));
      // La ocupación pudo cambiar (p. ej. la salida se llenó): se muestra la actual.
      departures.reload();
      setSubmitting(false);
    }
  }

  return (
    <>
      <ScreenHeader title="Reservar turno" />

      {active.data && (
        <div className="reserve__notice">
          <p>
            Ya tenés un turno activo para la salida de las{' '}
            {formatTime(active.data.departure.departsAt)}. Para reservar otro, primero cancelalo.
          </p>
          <Link to={`/turno/${encodeURIComponent(active.data.id)}`}>Ver mi turno</Link>
        </div>
      )}

      {catalog.loading ? (
        <p role="status" className="page-placeholder">
          Cargando núcleos…
        </p>
      ) : catalog.error ? (
        <p role="alert" className="reserve__alert">
          {catalog.error}
        </p>
      ) : !cores.length ? (
        <p className="page-placeholder">Todavía no hay núcleos disponibles para reservar.</p>
      ) : (
        <form className="reserve" onSubmit={handleSubmit}>
          <BuildingSelector
            buildings={buildings}
            cores={cores}
            value={coreId}
            onChange={chooseCore}
          />

          {core && (
            <OriginFloorInput floors={originFloors} value={origin} onChange={chooseOrigin} />
          )}

          {origin !== null &&
            (floors.loading ? (
              <p role="status" className="page-placeholder">
                Cargando pisos…
              </p>
            ) : floors.error ? (
              <p role="alert" className="reserve__alert">
                {floors.error}
              </p>
            ) : (
              <FloorSelector
                floors={floors.data ?? []}
                value={validDestination}
                onChange={setDestination}
              />
            ))}

          {core &&
            (departures.error ? (
              <p role="alert" className="reserve__alert">
                {departures.error}
              </p>
            ) : !departures.data ? (
              <p role="status" className="page-placeholder">
                Cargando salidas…
              </p>
            ) : !departures.data.length ? (
              <p className="page-placeholder">
                No hay salidas abiertas para reservar en este núcleo. Probá en unos minutos.
              </p>
            ) : (
              <SlotList
                departures={departures.data}
                value={departure?.id ?? null}
                onChange={setDepartureId}
              />
            ))}

          <div className="reserve__submit">
            {submitError && (
              <p role="alert" className="reserve__alert">
                {submitError}
              </p>
            )}
            <Button type="submit" disabled={!ready} loading={submitting} loadingLabel="Reservando…">
              {departure
                ? `Confirmar turno · ${formatTime(departure.departsAt)}`
                : 'Confirmar turno'}
            </Button>
          </div>
        </form>
      )}
    </>
  );
}
