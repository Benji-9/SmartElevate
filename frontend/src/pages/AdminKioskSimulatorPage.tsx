import { useId, useRef, useState } from 'react';
import { Button } from '../components/Button';
import { ElevatorSelect } from '../features/kiosk/ElevatorSelect';
import { KioskQr } from '../features/kiosk/KioskQr';
import { errorMessage, useResource } from '../hooks/useResource';
import { createKiosk, getKiosks } from '../services/api';
import { simulatedKiosks as simulated } from '../features/kiosk/simulatedKiosks';
import '../features/admin/admin.css';
import '../features/kiosk/kiosk.css';

/**
 * 09d · Simulador de kiosco (SCREENS.md): el mismo componente del kiosco con un kiosco
 * `simulated`. Existe solo si el servidor dice `simulatorEnabled`; no hay flag en el front.
 */
export function AdminKioskSimulatorPage() {
  const cardId = useId();
  const kiosks = useResource(getKiosks);
  const [elevator, setElevator] = useState('');
  const [secret, setSecret] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string>();
  // El último ascensor elegido: si cambia mientras se crea un kiosco, esa respuesta no se muestra.
  const selected = useRef('');

  const open = (code: string, fresh = false) => {
    selected.current = code;
    setElevator(code);
    setError(undefined);
    if (fresh) simulated.delete(code);
    const known = code ? simulated.get(code) : undefined;
    setSecret(known ?? null);
    if (!code || known) return;
    setCreating(true);
    createKiosk({ elevatorId: code, simulated: true })
      .then(
        (kiosk) => {
          simulated.set(code, kiosk.secret);
          if (selected.current === code) setSecret(kiosk.secret);
        },
        (e: unknown) => selected.current === code && setError(errorMessage(e)),
      )
      .finally(() => selected.current === code && setCreating(false));
  };

  if (kiosks.error) {
    return (
      <>
        <h1 className="text-title-lg">Simulador de kiosco</h1>
        <div role="alert" className="admin__error">
          <p>{kiosks.error}</p>
          <Button variant="secondary" block={false} onClick={kiosks.reload}>
            Reintentar
          </Button>
        </div>
      </>
    );
  }
  if (!kiosks.data) {
    return (
      <p role="status" className="page-placeholder">
        Cargando…
      </p>
    );
  }
  if (!kiosks.data.simulatorEnabled) {
    return (
      <>
        <h1 className="text-title-lg">Simulador de kiosco</h1>
        <p className="page-placeholder">El simulador de kiosco está apagado en este entorno.</p>
      </>
    );
  }

  return (
    <>
      <p className="simulator-banner">MODO SIMULADOR</p>
      <header>
        <h1 className="text-title-lg">Simulador de kiosco</h1>
        <p className="admin__context">
          Muestra el QR de un ascensor como la tablet del hall, para probar el check-in desde la
          app. Usa un kiosco simulado: los check-ins quedan marcados como del simulador.
        </p>
      </header>
      <section className="admin-card kiosk-card" aria-labelledby={cardId}>
        <h2 id={cardId} className="text-card-title">
          {elevator ? `Ascensor ${elevator}` : 'Elegí un ascensor'}
        </h2>
        <div className="kiosk-create">
          <ElevatorSelect value={elevator} onChange={(code) => open(code)} />
        </div>
        {creating && (
          <p role="status" className="page-placeholder">
            Creando el kiosco simulado…
          </p>
        )}
        {error && (
          <p role="alert" className="kiosk-error">
            {error}
          </p>
        )}
        {secret && (
          <KioskQr
            key={secret}
            secret={secret}
            elevator={elevator}
            unauthorizedAction={
              <Button block={false} onClick={() => open(elevator, true)}>
                Crear otro kiosco simulado
              </Button>
            }
          />
        )}
      </section>
    </>
  );
}
