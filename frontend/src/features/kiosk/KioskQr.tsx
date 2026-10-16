import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { encode } from 'uqr';
import { ApiError, getKioskQrToken } from '../../services/api';
import './kiosk.css';

/** Reintento ante un error de red o del servidor. */
const RETRY_SECONDS = 10;
/** Espera si el 429 no trae `Retry-After`. */
const RATE_LIMIT_SECONDS = 30;
/** Piso entre pedidos: si `expiresAt` ya pasó no pide en loop (el servidor igual tiene tope). */
const MIN_DELAY_MS = 1000;

type State =
  | { kind: 'loading' }
  | { kind: 'ready'; token: string }
  | { kind: 'unauthorized' }
  | { kind: 'mismatch'; credentialElevator: string }
  | { kind: 'retrying'; message: string };

/** QR en SVG: un `path` con un cuadrado por módulo oscuro, con margen de 4 módulos. */
function QrImage({ text, label }: { text: string; label: string }) {
  const { size, path } = useMemo(() => {
    const qr = encode(text, { ecc: 'M', border: 4 });
    let d = '';
    qr.data.forEach((row, y) =>
      row.forEach((dark, x) => {
        if (dark) d += `M${x} ${y}h1v1h-1z`;
      }),
    );
    return { size: qr.size, path: d };
  }, [text]);
  return (
    <svg
      className="kiosk-qr__image"
      viewBox={`0 0 ${size} ${size}`}
      role="img"
      aria-label={label}
      shapeRendering="crispEdges"
    >
      <path d={path} />
    </svg>
  );
}

/**
 * Kiosco del QR (SCREENS.md §10): pide el token con la credencial del kiosco, lo muestra y pide
 * el siguiente al llegar a `expiresAt`. Con 401 (credencial inválida o revocada) deja de pedir;
 * con 429 espera lo que diga `Retry-After`. Si la credencial es de otro ascensor que `elevator`,
 * avisa y deja de pedir: no muestra un QR que registraría el viaje en otro ascensor. Lo usan `/qr/:ascensor` y el simulador del admin.
 * Montarlo con `key={secret}`: al cambiar de credencial no tiene que verse el QR anterior.
 */
export function KioskQr({
  secret,
  elevator,
  unauthorizedAction,
  footer,
}: {
  secret: string;
  /** Ascensor esperado (el de la URL): si la credencial es de otro, no se muestra el QR. */
  elevator: string;
  /** Qué ofrecer si la credencial ya no sirve o es de otro ascensor (borrarla, crear otra). */
  unauthorizedAction?: ReactNode;
  /** Debajo del QR mientras la credencial sirve. */
  footer?: ReactNode;
}) {
  const [state, setState] = useState<State>({ kind: 'loading' });
  const [cycle, setCycle] = useState(0);

  useEffect(() => {
    let alive = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const nextIn = (ms: number) => {
      timer = setTimeout(() => setCycle((c) => c + 1), ms);
    };
    getKioskQrToken(secret).then(
      ({ token, msLeft, elevatorId }) => {
        if (!alive) return;
        // Credencial de otro ascensor: su QR registraría el check-in en ese otro. Deja de pedir.
        if (elevatorId !== elevator) {
          setState({ kind: 'mismatch', credentialElevator: elevatorId });
          return;
        }
        setState({ kind: 'ready', token });
        nextIn(Math.max(msLeft, MIN_DELAY_MS));
      },
      (error: unknown) => {
        if (!alive) return;
        if (error instanceof ApiError && error.status === 401) {
          setState({ kind: 'unauthorized' });
          return;
        }
        const limited = error instanceof ApiError && error.status === 429;
        const seconds = limited ? (error.retryAfter ?? RATE_LIMIT_SECONDS) : RETRY_SECONDS;
        setState({
          kind: 'retrying',
          message: limited
            ? `Hicimos demasiados pedidos. Reintentamos en ${seconds} s.`
            : `No pudimos conectarnos. Reintentamos en ${seconds} s.`,
        });
        nextIn(seconds * 1000);
      },
    );
    return () => {
      alive = false;
      clearTimeout(timer);
    };
  }, [secret, elevator, cycle]);

  if (state.kind === 'mismatch') {
    return (
      <div role="alert" className="kiosk-alert">
        <p className="text-heading">Esta credencial es de otro ascensor</p>
        <p>
          La credencial es del ascensor {state.credentialElevator} y esta pantalla es del ascensor{' '}
          {elevator}. Para no registrar viajes en otro ascensor, no mostramos el QR. Cargá la
          credencial del ascensor {elevator}.
        </p>
        {unauthorizedAction}
      </div>
    );
  }

  if (state.kind === 'unauthorized') {
    return (
      <div role="alert" className="kiosk-alert">
        <p className="text-heading">Este kiosco no está habilitado</p>
        <p>
          La credencial no es válida o fue revocada. Pedile una nueva a quien administra
          SmartElevate.
        </p>
        {unauthorizedAction}
      </div>
    );
  }

  return (
    <div className="kiosk-qr">
      {state.kind === 'ready' ? (
        <QrImage
          text={state.token}
          label={`Código QR del ascensor ${elevator} para hacer check-in`}
        />
      ) : (
        <div className="kiosk-qr__placeholder">
          <p role="status">{state.kind === 'loading' ? 'Cargando el código…' : state.message}</p>
        </div>
      )}
      <p className="text-heading">Escaneá el QR desde la app SmartElevate</p>
      <p className="kiosk-qr__hint">
        El código cambia cada pocos segundos: escanealo acá, no le saques foto.
      </p>
      {footer}
    </div>
  );
}
