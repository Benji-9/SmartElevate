import { useEffect, useEffectEvent, useId, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { Button } from '../components/Button';
import { Chip } from '../components/Chip';
import { Input } from '../components/Input';
import { ScreenHeader } from '../components/ScreenHeader';
import { createQrReader } from '../features/check-in/qrReader';
import '../features/check-in/check-in.css';
import { formatFloor, formatSlot } from '../features/turn/format';
import '../features/turn/turn.css';
import { useDemoMode } from '../hooks/useDemoMode';
import { errorMessage, useResource } from '../hooks/useResource';
import { checkIn, getActiveReservation } from '../services/api';

type Camera = 'starting' | 'on' | 'denied' | 'unavailable';

const SCAN_INTERVAL_MS = 300;
/** Código de "Simular escaneo del QR" (modo demo): los mocks lo dan por cumplido. */
const DEMO_CODE = 'DEMO';

/**
 * Check-in (Figma 06 y 06b). Escanea el QR del ascensor con la cámara; en `/check-in/codigo`
 * (`manual`) abre el bottom sheet para tipear el código. Con el resultado va a `/check-in/ok`.
 */
export function CheckInPage({ manual = false }: { manual?: boolean }) {
  const navigate = useNavigate();
  const demo = useDemoMode();
  const videoRef = useRef<HTMLVideoElement>(null);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const failedCode = useRef<string | null>(null);
  const [camera, setCamera] = useState<Camera>(() =>
    'mediaDevices' in navigator ? 'starting' : 'unavailable',
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const turn = useResource(getActiveReservation);
  const turnTitleId = useId();
  const codeTitleId = useId();
  const codeDescriptionId = useId();

  async function submit(value: string) {
    setSubmitting(true);
    setError(null);
    try {
      const result = await checkIn({ code: value });
      navigate('/check-in/ok', { state: result });
    } catch (submitError) {
      failedCode.current = value;
      setError(errorMessage(submitError));
      setSubmitting(false);
    }
  }

  // No reintenta el mismo QR que ya falló: el siguiente token (rota) sí se envía.
  const onScan = useEffectEvent((value: string) => {
    if (!manual && !submitting && value !== failedCode.current) void submit(value);
  });

  useEffect(() => {
    if (!('mediaDevices' in navigator)) return;
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
  }, []);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (manual && !dialog.open) dialog.showModal();
    if (!manual && dialog.open) dialog.close();
  }, [manual]);

  function closeManual() {
    setError(null);
    navigate('/check-in', { replace: true });
  }

  let cameraState;
  if (camera === 'denied' || camera === 'unavailable') {
    cameraState = (
      <section className="check-in__message" aria-labelledby="check-in-camera-title">
        <h2 id="check-in-camera-title">
          {camera === 'denied'
            ? 'No tenemos permiso para usar la cámara'
            : 'No pudimos usar la cámara'}
        </h2>
        <p>
          {camera === 'denied'
            ? 'Habilitala desde la configuración del navegador o ingresá el código que aparece debajo del QR del ascensor.'
            : 'Ingresá el código que aparece debajo del QR del ascensor.'}
        </p>
      </section>
    );
  } else {
    cameraState = (
      <>
        <div className="check-in__finder" aria-hidden="true" />
        {camera === 'starting' && <p role="status">Pidiendo permiso para usar la cámara…</p>}
        <p className="check-in__instructions">Apuntá la cámara al QR de la pantalla del ascensor</p>
        <p className="check-in__note">El código rota: escanealo desde dentro de la cabina</p>
      </>
    );
  }

  const reservation = turn.data;
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
      <p className="page-placeholder">
        No tenés un turno activo. <Link to="/reservar">Reservá un turno</Link>
      </p>
    );
  } else {
    turnContent = (
      <>
        <Chip>
          {formatSlot(reservation.departure.departsAt, reservation.departure.durationMinutes)}
        </Chip>
        <p>
          <strong>Ascensor {reservation.core.name}</strong> · {formatFloor(reservation.originFloor)}{' '}
          <span aria-hidden="true">→</span>
          <span className="visually-hidden">a</span> {formatFloor(reservation.destinationFloor)}
        </p>
        <p className="page-placeholder">{reservation.core.hall}</p>
      </>
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
              onClick={() => void submit(DEMO_CODE)}
            >
              Simular escaneo del QR
            </Button>
          )}
          {submitting && !manual && <p role="status">Registrando tu check-in…</p>}
          {error && !manual && (
            <p role="alert" className="turn-alert check-in__alert">
              {error}
            </p>
          )}
        </div>
      </div>

      <section className="check-in__sheet" aria-labelledby={turnTitleId}>
        <h2 id={turnTitleId}>Tu turno</h2>
        {turnContent}
        <Link to="/check-in/codigo" replace className="button button--secondary">
          Ingresar código manualmente
        </Link>
      </section>

      <dialog
        ref={dialogRef}
        className="check-in__sheet check-in__code"
        aria-labelledby={codeTitleId}
        aria-describedby={codeDescriptionId}
        onClose={() => manual && closeManual()}
      >
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void submit(code.trim());
          }}
        >
          <h2 id={codeTitleId}>Ingresá el código</h2>
          <p id={codeDescriptionId} className="page-placeholder">
            Está debajo del QR, en la pantalla del ascensor. Rota igual que el QR.
          </p>
          <Input
            label="Código del ascensor"
            value={code}
            onChange={(event) => setCode(event.target.value)}
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            required
          />
          {error && manual && (
            <p role="alert" className="turn-alert">
              {error}
            </p>
          )}
          <Button type="submit" loading={submitting} loadingLabel="Registrando tu check-in…">
            Confirmar check-in
          </Button>
          <Button variant="secondary" disabled={submitting} onClick={closeManual}>
            Volver a escanear
          </Button>
        </form>
      </dialog>
    </div>
  );
}
