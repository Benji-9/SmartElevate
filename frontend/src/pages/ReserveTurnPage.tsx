import { useEffect, useId, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { Button } from '../components/Button';
import { ScreenHeader } from '../components/ScreenHeader';
import { BuildingSelector } from '../features/turn/BuildingSelector';
import { FloorSelector } from '../features/turn/FloorSelector';
import { formatFloor, formatSlot, formatTime } from '../features/turn/format';
import { loadLastTrip, saveLastTrip } from '../features/turn/lastTrip';
import { OriginFloorInput } from '../features/turn/OriginFloorInput';
import { SlotList } from '../features/turn/SlotList';
import '../features/turn/ReserveTurn.css';
import { errorMessage, useResource } from '../hooks/useResource';
import {
  ApiError,
  getActiveReservation,
  getBuildings,
  getCores,
  getDepartures,
  getDestinationFloors,
  reserve,
} from '../services/api';
import type { Building } from '../types/api';
import type { Core, Departure } from '../types/pending';
import { Skeleton } from '../components/Skeleton';

const loadCatalog = () => Promise.all([getBuildings(), getCores()]);
// Si no se puede saber, se muestra el formulario: el servidor rechaza la reserva igual.
const loadActive = () => getActiveReservation().catch(() => null);

/** Pisos de origen posibles: los del núcleo dentro del rango de su edificio. */
function originFloorsOf(core: Core | undefined, buildings: Building[]) {
  const building = buildings.find((b) => b.code === core?.buildingId);
  return core && building
    ? core.floors.filter((f) => f >= building.minFloor && f <= building.maxFloor)
    : [];
}

export function ReserveTurnPage() {
  const navigate = useNavigate();
  const [coreId, setCoreId] = useState<string | null>(null);
  const [origin, setOrigin] = useState<number | null>(null);
  const [destination, setDestination] = useState<number | null>(null);
  const [departureId, setDepartureId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  // Salida rechazada con 409 al confirmar: puede ser que se haya llenado.
  const [rejected, setRejected] = useState<Departure | null>(null);
  const slotsLabel = useRef<HTMLSpanElement>(null);
  const missingId = useId();
  const [lastTrip] = useState(loadLastTrip);
  const [prefillDone, setPrefillDone] = useState(false);
  // Lo elegido salió del último viaje y todavía no se tocó.
  const [fromLastTrip, setFromLastTrip] = useState(false);

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

  // Con el catálogo cargado, se precarga el último viaje si sigue siendo válido (la franja nunca).
  // El destino se valida contra los pisos elegibles más abajo (`validDestination`).
  if (!prefillDone && catalog.data) {
    setPrefillDone(true);
    const savedCore = cores.find((c) => c.id === lastTrip?.coreId);
    if (lastTrip && savedCore) {
      setCoreId(savedCore.id);
      if (originFloorsOf(savedCore, buildings).includes(lastTrip.originFloor)) {
        setOrigin(lastTrip.originFloor);
        setDestination(lastTrip.destinationFloor);
      }
      setFromLastTrip(true);
    }
  }

  const core = cores.find((c) => c.id === coreId);
  const originFloors = originFloorsOf(core, buildings);
  // Si la elección dejó de ser válida (p. ej. la salida se llenó al recargar), no cuenta.
  const validDestination = floors.data?.some((f) => f.floor === destination && f.eligible)
    ? destination
    : null;
  const departure = departures.data?.find((d) => d.id === departureId && d.occupied < d.capacity);
  // Con las salidas ya recargadas, la rechazada no tiene lugar: se llenó.
  const filled =
    rejected &&
    !departures.loading &&
    departures.data &&
    !departures.data.some((d) => d.id === rejected.id && d.occupied < d.capacity)
      ? rejected
      : null;
  const alertMessage = filled
    ? `La salida de las ${formatTime(filled.departsAt)} se llenó. Elegí otra.`
    : rejected && departures.loading
      ? null
      : submitError;
  const ready = origin !== null && validDestination !== null && departure !== undefined;
  // "Lima 3 · Piso 7 · 7:25 – 7:27" con lo que ya se eligió.
  const summary = [
    core?.name,
    validDestination !== null && `Piso ${formatFloor(validDestination)}`,
    departure && formatSlot(departure.departsAt, departure.durationMinutes),
  ]
    .filter(Boolean)
    .join(' · ');
  // El próximo paso, para que el botón deshabilitado diga qué falta.
  const missing = !core
    ? 'Falta elegir el edificio'
    : origin === null
      ? 'Falta elegir el piso de origen'
      : validDestination === null
        ? 'Falta elegir el piso de destino'
        : !departure
          ? 'Falta elegir la franja horaria'
          : null;

  // La lista de franjas cambió: se lleva el foco ahí para elegir otra.
  useEffect(() => {
    if (filled) slotsLabel.current?.focus();
  }, [filled]);

  function clearSubmitError() {
    setSubmitError(null);
    setRejected(null);
  }

  function chooseCore(id: string) {
    setFromLastTrip(false);
    setCoreId(id);
    setOrigin(null);
    setDestination(null);
    setDepartureId(null);
    clearSubmitError();
  }

  function chooseDeparture(id: string) {
    setDepartureId(id);
    clearSubmitError();
  }

  function chooseOrigin(floor: number) {
    setFromLastTrip(false);
    setOrigin(floor);
    setDestination(null);
  }

  function chooseDestination(floor: number) {
    setFromLastTrip(false);
    setDestination(floor);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (origin === null || validDestination === null || !departure) return;
    setSubmitting(true);
    clearSubmitError();
    try {
      const reservation = await reserve({
        departureId: departure.id,
        originFloor: origin,
        destinationFloor: validDestination,
      });
      saveLastTrip({
        coreId: departure.coreId,
        originFloor: origin,
        destinationFloor: validDestination,
      });
      navigate(`/turno/${encodeURIComponent(reservation.id)}`);
    } catch (error) {
      setSubmitError(errorMessage(error));
      if (error instanceof ApiError && error.status === 409) setRejected(departure);
      // La ocupación pudo cambiar (p. ej. la salida se llenó): se muestra la actual.
      departures.reload();
      setSubmitting(false);
    }
  }

  return (
    <>
      <ScreenHeader title="Reservar turno" className="reserve__header" />

      {catalog.loading || active.loading ? (
        <Skeleton label="Cargando núcleos…" rows={3} height={44} />
      ) : active.data ? (
        // Con un turno activo no se puede reservar otro (turnos.md §3): no se ofrece el formulario.
        <div className="reserve__notice">
          <p>
            Ya tenés un turno activo para la salida de las{' '}
            {formatTime(active.data.departure.departsAt)}. Para reservar otro, primero cancelalo.
          </p>
          <Link
            to={`/turno/${encodeURIComponent(active.data.id)}`}
            className="button button--primary button--inline"
          >
            Ver mi turno
          </Link>
        </div>
      ) : catalog.error ? (
        <p role="alert" className="reserve__alert">
          {catalog.error}
        </p>
      ) : !cores.length ? (
        <p className="page-placeholder">Todavía no hay núcleos disponibles para reservar.</p>
      ) : (
        // Columnas del layout de Pantalla (VIEW-MODES.md §6); en el móvil no existen.
        <form className="reserve" onSubmit={handleSubmit}>
          <div className="reserve__column">
            {fromLastTrip && core && (
              <p className="reserve__notice">
                Usamos tu último viaje: {core.name}
                {origin !== null && ` · ${formatFloor(origin)}`}
                {validDestination !== null && ` → ${formatFloor(validDestination)}`}. Podés
                cambiarlo abajo.
              </p>
            )}

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
                <Skeleton label="Cargando pisos…" rows={2} height={44} />
              ) : floors.error ? (
                <p role="alert" className="reserve__alert">
                  {floors.error}
                </p>
              ) : (
                <FloorSelector
                  floors={floors.data ?? []}
                  value={validDestination}
                  onChange={chooseDestination}
                />
              ))}
          </div>

          <div className="reserve__column reserve__column--aside">
            {core &&
              (departures.error ? (
                <p role="alert" className="reserve__alert">
                  {departures.error}
                </p>
              ) : !departures.data ? (
                <Skeleton label="Cargando salidas…" rows={4} height={56} />
              ) : !departures.data.length ? (
                <p className="page-placeholder">
                  No hay salidas abiertas para reservar en este núcleo. Probá en unos minutos.
                </p>
              ) : (
                <SlotList
                  departures={departures.data}
                  value={departure?.id ?? null}
                  onChange={chooseDeparture}
                  labelRef={slotsLabel}
                />
              ))}

            <div className="reserve__submit">
              <div className="reserve__status">
                {/* En el móvil el "Elegí tu turno" vacío no se muestra: ya lo dice lo que falta. */}
                <p
                  className={[
                    'reserve__summary text-body-strong',
                    !summary && 'reserve__summary--empty',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                >
                  {summary || 'Elegí tu turno'}
                </p>
                {missing && (
                  <p id={missingId} className="reserve__missing">
                    {missing}
                  </p>
                )}
              </div>
              {alertMessage && (
                <p role="alert" className="reserve__alert">
                  {alertMessage}
                </p>
              )}
              <Button
                type="submit"
                block={false}
                disabled={!ready}
                aria-describedby={missing ? missingId : undefined}
                loading={submitting}
                loadingLabel="Reservando…"
              >
                {departure
                  ? `Confirmar turno · ${formatTime(departure.departsAt)}`
                  : 'Confirmar turno'}
              </Button>
            </div>
          </div>
        </form>
      )}
    </>
  );
}
