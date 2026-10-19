import { useEffect, useEffectEvent, useId, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { Button } from '../components/Button';
import { Chip } from '../components/Chip';
import { Mascot } from '../components/Mascot';
import { ScreenHeader } from '../components/ScreenHeader';
import { QR_INSTRUCTION } from '../features/check-in/qrInstruction';
import { createQrReader } from '../features/check-in/qrReader';
import '../features/check-in/check-in.css';
import { formatFloor, formatTime } from '../features/turn/format';
import '../features/turn/turn.css';
import { useNow } from '../hooks/useNow';
import { useDemoMode } from '../hooks/useDemoMode';
import { errorMessage, useResource } from '../hooks/useResource';
import { ApiError, checkIn, getActiveReservation } from '../services/api';

type Camera = 'starting' | 'on' | 'denied' | 'unavailable';
type CheckInError = { message: string; code?: string };

const SCAN_INTERVAL_MS = 300;
/** Token de "Simular escaneo del QR" (modo demo): los mocks lo dan por cumplido. */
const DEMO_TOKEN = 'DEMO';
/** Con estos rechazos otro QR no cambia nada: se deja de escanear. */
const FINAL_CODES = ['NO_ACTIVE_RESERVATION', 'ALREADY_CHECKED_IN'];

/** Qué puede hacer la persona después de cada rechazo (además del mensaje del servidor). */
function ErrorAction({ code }: { code?: string }) {
  if (code === 'NO_ACTIVE_RESERVATION') return <Link to="/reservar">Reservá un turno</Link>;
  if (code === 'ALREADY_CHECKED_IN') return <Link to="/">Volver al inicio</Link>;
  if (code === 'OUTSIDE_CHECK_IN_WINDOW') {
    return <>Escaneá el QR cerca de la hora de salida de tu turno.</>;
  }
  return null;
}

/**
 * Check-in (Figma 06). Escanea el QR del ascensor con la cámara y manda el token a
 * `POST /api/check-ins`; con el resultado va a `/check-in/ok`. No hay ingreso manual: el token
 * es largo y opaco (docs/alcance-mvp.md).
 */
export function CheckInPage() {
  const navigate = useNavigate();
  const demo = useDemoMode();
  const videoRef = useRef<HTMLVideoElement>(null);
  const failedToken = useRef<string | null>(null);
  const [camera, setCamera] = useState<Camera>(() =>
    'mediaDevices' in navigator ? 'starting' : 'unavailable',
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<CheckInError | null>(null);
  const turn = useResource(getActiveReservation);
  const turnTitleId = useId();
  const reservation = turn.data;
  const now = useNow(1_000);
  // Antes de la apertura no se pide la cámara: se avisa a qué hora abre (#285).
  const opensAt = reservation?.checkInOpensAt;
  const notYetOpen = opensAt !== undefined && now < Date.parse(opensAt);
  // La cámara se pide solo con turno activo (#152). Si falla la carga del turno se pide igual:
  // el servidor valida el check-in y no queremos trabar a quien sí tiene turno.
  const wantsCamera =
    !turn.loading && !notYetOpen && (turn.error !== undefined || reservation != null);
  const scanDone = error?.code !== undefined && FINAL_CODES.includes(error.code);

  async function submit(token: string) {
    setSubmitting(true);
    setError(null);
    try {
      const result = await checkIn({ token });
      navigate('/check-in/ok', { state: result });
    } catch (submitError) {
      failedToken.current = token;
      setError({
        message: errorMessage(submitError),
        code: submitError instanceof ApiError ? submitError.code : undefined,
      });
      setSubmitting(false);
    }
  }

  // No reintenta el mismo QR que ya falló: el siguiente token (rota) sí se envía.
  const onScan = useEffectEvent((value: string) => {
    if (!submitting && !scanDone && value !== failedToken.current) void submit(value);
  });

  useEffect(() => {
    if (!wantsCamera || !('mediaDevices' in navigator)) return;
    let stopped = false;
    let stream: MediaStream | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const release = () => stream?.getTracks().forEach((track) => track.stop());

    async function start() {
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment' },
          audio: false,
        });
      } catch (cameraError) {
        const denied =
          cameraError instanceof DOMException && cameraError.name === 'NotAllowedError';
        if (!stopped) setCamera(denied ? 'denied' : 'unavailable');
        return;
      }
      const video = videoRef.current;
      if (stopped || !video) return release();
      video.srcObject = stream;
      setCamera('on');

      let read;
      try {
        read = await createQrReader();
      } catch {
        release();
        if (!stopped) setCamera('unavailable');
        return;
      }
      const tick = async () => {
        if (stopped) return;
        const value = await read(video).catch(() => null);
        if (value) onScan(value);
        if (!stopped) timer = setTimeout(tick, SCAN_INTERVAL_MS);
      };
      void tick();
    }

    void start();
    return () => {
      stopped = true;
      clearTimeout(timer);
      release();
    };
  }, [wantsCamera]);

  let cameraState;
  if (notYetOpen) {
    cameraState = (
      <section className="check-in__message" aria-labelledby="check-in-closed-title">
        <Mascot pose="duerme" size={96} />
        <h2 id="check-in-closed-title">Abre a las {formatTime(opensAt)} hs</h2>
        <p>Cuando abra, vas a poder escanear el QR del ascensor desde acá.</p>
      </section>
    );
  } else if (!wantsCamera) {
    cameraState = null;
  } else if (camera === 'denied' || camera === 'unavailable') {
    cameraState = (
      <section className="check-in__message" aria-labelledby="check-in-camera-title">
        <Mascot pose="triste" size={96} />
        <h2 id="check-in-camera-title">
          {camera === 'denied'
            ? 'No tenemos permiso para usar la cámara'
            : 'No pudimos usar la cámara'}
        </h2>
        <p>
          {camera === 'denied'
            ? 'Para hacer check-in hay que escanear el QR del ascensor. Habilitá la cámara desde la configuración del navegador.'
            : 'Para hacer check-in hay que escanear el QR del ascensor. Probá con otro navegador o con el celular.'}
        </p>
      </section>
    );
  } else {
    cameraState = (
      <>
        <div className="check-in__finder" aria-hidden="true" />
        {camera === 'starting' && <p role="status">Pidiendo permiso para usar la cámara…</p>}
        <p className="check-in__instructions">Apuntá la cámara al QR de la pantalla del ascensor</p>
        <p className="check-in__note">{QR_INSTRUCTION} El código rota: no le saques foto.</p>
      </>
    );
  }

  let turnContent;
  if (turn.loading) {
    turnContent = (
      <p role="status" className="page-placeholder">
        Cargando tu turno…
      </p>
    );
  } else if (turn.error) {
    turnContent = (
      <p role="alert" className="turn-alert">
        {turn.error}
      </p>
    );
  } else if (!reservation) {
    turnContent = (
      <div className="empty-state">
        <Mascot pose="senala" size={96} />
        <p className="page-placeholder">
          No tenés un turno activo. <Link to="/reservar">Reservá un turno</Link>
        </p>
      </div>
    );
  } else {
    turnContent = (
      <p className="check-in__route text-body-sm">
        {reservation.coreName} · Ascensor {reservation.elevator} · Piso{' '}
        {formatFloor(reservation.destinationFloor)}
      </p>
    );
  }

  return (
    <div className="check-in">
      <div className="check-in__view">
        <video
          ref={videoRef}
          className="check-in__video"
          hidden={camera !== 'on'}
          autoPlay
          muted
          playsInline
          aria-hidden="true"
        />
        <ScreenHeader title="Check-in" />
        <div className="check-in__body">
          {cameraState}
          {demo && (
            <Button
              variant="secondary"
              className="check-in__demo"
              disabled={submitting}
              onClick={() => void submit(DEMO_TOKEN)}
            >
              Simular escaneo del QR
            </Button>
          )}
          {submitting && <p role="status">Registrando tu check-in…</p>}
          {error && (
            <p role="alert" className="turn-alert check-in__alert">
              {error.message} <ErrorAction code={error.code} />
            </p>
          )}
        </div>
      </div>

      <section className="check-in__sheet" aria-labelledby={turnTitleId}>
        <div className="check-in__sheet-head">
          <h2 id={turnTitleId} className="text-card-title">
            Tu turno
          </h2>
          {reservation && <Chip>{formatTime(reservation.departureAt)} hs</Chip>}
        </div>
        {turnContent}
      </section>
    </div>
  );
}
