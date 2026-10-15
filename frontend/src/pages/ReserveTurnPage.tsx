import { useId, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { Button } from '../components/Button';
import { ScreenHeader } from '../components/ScreenHeader';
import { BuildingSelector } from '../features/turn/BuildingSelector';
import {
  coreOptions,
  destinationFloorsOf,
  elevatorsServing,
  originFloorsOf,
  slotsOf,
} from '../features/turn/catalog';
import { FloorSelector } from '../features/turn/FloorSelector';
import { formatFloor, formatTime } from '../features/turn/format';
import { loadLastTrip, saveLastTrip } from '../features/turn/lastTrip';
import { OriginFloorInput } from '../features/turn/OriginFloorInput';
import { SlotList } from '../features/turn/SlotList';
import '../features/turn/ReserveTurn.css';
import { errorMessage, useResource } from '../hooks/useResource';
import {
  ApiError,
  getBuildings,
  getDepartures,
  getElevators,
  getActiveReservation,
  reserve,
} from '../services/api';
import { Skeleton } from '../components/Skeleton';

const loadCatalog = () => Promise.all([getBuildings(), getElevators()]);
// Si no se puede saber, se muestra el formulario: el servidor rechaza la reserva igual.
const loadActive = () => getActiveReservation().catch(() => null);

// Rechazos de POST /reservations por `code`. El texto es el del servidor (trae los valores de la
// regla, p. ej. hasta cuándo dura la suspensión); el código decide qué más hace la pantalla.
/** La salida ya no sirve: se recargan las salidas y se elige otra. */
const SLOT_ERRORS = new Set([
  'TURN_FULL',
  'ELEVATOR_NOT_BOOKABLE',
  'OUTSIDE_BOOKING_WINDOW',
  'DEPARTURE_NOT_FOUND',
]);
/** El destino no vale para este viaje (o este usuario): se elige otro. */
const FLOOR_ERRORS = new Set([
  'SAME_FLOOR',
  'FLOOR_OUT_OF_RANGE',
  'FLOOR_NOT_SERVED',
  'LOW_FLOOR_DESTINATION',
]);

export function ReserveTurnPage() {
  const navigate = useNavigate();
  const [coreCode, setCoreCode] = useState<string | null>(null);
  // Las salidas son por edificio: se guarda aparte para no pedirlas de nuevo al cambiar de núcleo.
  const [buildingCode, setBuildingCode] = useState<string | null>(null);
  const [origin, setOrigin] = useState<number | null>(null);
  const [destination, setDestination] = useState<number | null>(null);
  const [slotTime, setSlotTime] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const slotsLabel = useRef<HTMLSpanElement>(null);
  const missingId = useId();
  const [lastTrip] = useState(loadLastTrip);
  const [prefillDone, setPrefillDone] = useState(false);
  // Lo elegido salió del último viaje y todavía no se tocó.
  const [fromLastTrip, setFromLastTrip] = useState(false);

  const catalog = useResource(loadCatalog);
  const active = useResource(loadActive);
  const cores = catalog.data ? coreOptions(...catalog.data) : [];
  const core = cores.find((c) => c.code === coreCode);
  const departures = useResource(
    useMemo(() => (buildingCode ? () => getDepartures(buildingCode) : null), [buildingCode]),
  );

  // Con el catálogo cargado, se precarga el último viaje si sigue siendo válido (la franja nunca).
  // El destino se valida contra los pisos posibles más abajo (`validDestination`).
  if (!prefillDone && catalog.data) {
    setPrefillDone(true);
    const savedCore = cores.find((c) => c.code === lastTrip?.coreId);
    if (lastTrip && savedCore) {
      setCoreCode(savedCore.code);
      setBuildingCode(savedCore.building.code);
      if (originFloorsOf(savedCore).includes(lastTrip.originFloor)) {
        setOrigin(lastTrip.originFloor);
        setDestination(lastTrip.destinationFloor);
      }
      setFromLastTrip(true);
    }
  }

  const originFloors = originFloorsOf(core);
  const destinationFloors = core && origin !== null ? destinationFloorsOf(core, origin) : [];
  // Si la elección dejó de ser válida (p. ej. otro núcleo), no cuenta.
  const validDestination =
    destination !== null && destinationFloors.includes(destination) ? destination : null;
  // Solo las salidas de los ascensores que paran en los pisos elegidos.
  const slots =
    core && departures.data
      ? slotsOf(
          departures.data,
          elevatorsServing(
            core,
            [origin, validDestination].filter((f) => f !== null),
          ),
        )
      : undefined;
  const slot = slots?.find((s) => s.departureAt === slotTime && s.departure.remainingSeats > 0);
  const ready = origin !== null && validDestination !== null && slot !== undefined;
  // "Lima 3 · Piso 7 · 7:25" con lo que ya se eligió.
  const summary = [
    core?.name,
    validDestination !== null && `Piso ${formatFloor(validDestination)}`,
    slot && formatTime(slot.departureAt),
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
        : !slot
          ? 'Falta elegir la franja horaria'
          : null;

  function chooseCore(code: string) {
    setFromLastTrip(false);
    setCoreCode(code);
    setBuildingCode(cores.find((c) => c.code === code)?.building.code ?? null);
    setOrigin(null);
    setDestination(null);
    setSlotTime(null);
    setSubmitError(null);
  }

  function chooseSlot(departureAt: string) {
    setSlotTime(departureAt);
    setSubmitError(null);
  }

  function chooseOrigin(floor: number) {
    setFromLastTrip(false);
    setOrigin(floor);
    setDestination(null);
    setSubmitError(null);
  }

  function chooseDestination(floor: number) {
    setFromLastTrip(false);
    setDestination(floor);
    setSubmitError(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!core || origin === null || validDestination === null || !slot) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const reservation = await reserve({
        departureId: slot.departure.id,
        originFloor: origin,
        destinationFloor: validDestination,
      });
      saveLastTrip({ coreId: core.code, originFloor: origin, destinationFloor: validDestination });
      navigate(`/turno/${reservation.id}`);
    } catch (error) {
      setSubmitError(errorMessage(error));
      const code = error instanceof ApiError ? error.code : undefined;
      if (code && SLOT_ERRORS.has(code)) {
        // Se muestran los lugares actuales y el foco va a las franjas para elegir otra.
        setSlotTime(null);
        slotsLabel.current?.focus();
      } else if (code && FLOOR_ERRORS.has(code)) {
        setDestination(null);
      } else if (code === 'ACTIVE_LIMIT_REACHED') {
        // Ya tiene un turno (p. ej. reservado desde otro dispositivo): se ofrece ir a verlo.
        active.reload();
      }
      // El cupo pudo cambiar en cualquier caso.
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
            Ya tenés un turno activo para la salida de las {formatTime(active.data.departureAt)}.
            Para reservar otro, primero cancelalo.
          </p>
          <Link to={`/turno/${active.data.id}`} className="button button--primary button--inline">
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

            <BuildingSelector cores={cores} value={coreCode} onChange={chooseCore} />

            {core && (
              <OriginFloorInput floors={originFloors} value={origin} onChange={chooseOrigin} />
            )}

            {origin !== null && (
              <FloorSelector
                floors={destinationFloors}
                value={validDestination}
                onChange={chooseDestination}
              />
            )}
          </div>

          <div className="reserve__column reserve__column--aside">
            {core &&
              (departures.error ? (
                <p role="alert" className="reserve__alert">
                  {departures.error}
                </p>
              ) : !slots ? (
                <Skeleton label="Cargando salidas…" rows={4} height={56} />
              ) : !slots.length ? (
                <p className="page-placeholder">
                  No hay salidas que puedas reservar en este núcleo ahora. Probá en unos minutos.
                </p>
              ) : (
                <SlotList
                  slots={slots}
                  value={slot?.departureAt ?? null}
                  onChange={chooseSlot}
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
              {submitError && (
                <p role="alert" className="reserve__alert">
                  {submitError}
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
                {slot ? `Confirmar turno · ${formatTime(slot.departureAt)}` : 'Confirmar turno'}
              </Button>
            </div>
          </div>
        </form>
      )}
    </>
  );
}
