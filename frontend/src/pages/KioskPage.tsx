import { useState, type FormEvent } from 'react';
import { useParams } from 'react-router';
import { Button } from '../components/Button';
import { ConfirmAction } from '../components/ConfirmAction';
import { Input } from '../components/Input';
import { KioskQr } from '../features/kiosk/KioskQr';
import '../features/kiosk/kiosk.css';

const storageKey = (elevator: string) => `smartelevate.kiosk.${elevator}`;

// Con el storage bloqueado la credencial vale mientras la pestaña siga abierta.
function readSecret(elevator: string) {
  try {
    return localStorage.getItem(storageKey(elevator));
  } catch {
    return null;
  }
}

function writeSecret(elevator: string, secret: string | null) {
  try {
    if (secret) localStorage.setItem(storageKey(elevator), secret);
    else localStorage.removeItem(storageKey(elevator));
  } catch {
    // Sigue en memoria.
  }
}

/**
 * 10 · Kiosco QR del hall (SCREENS.md §10). Sin sesión de usuario: la credencial del kiosco se
 * pega una vez y queda en este dispositivo; nunca va en la URL ni a la vista.
 */
export function KioskPage() {
  const { ascensor = '' } = useParams();
  const [secret, setSecret] = useState(() => readSecret(ascensor));
  const [draft, setDraft] = useState('');
  const [error, setError] = useState<string>();

  const save = (secretValue: string | null) => {
    writeSecret(ascensor, secretValue);
    setSecret(secretValue);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const value = draft.trim();
    if (!value) {
      setError('Pegá la credencial del kiosco.');
      return;
    }
    setDraft('');
    setError(undefined);
    save(value);
  };

  if (!secret) {
    return (
      <main className="kiosk">
        <h1 className="text-title-lg">Kiosco del ascensor {ascensor}</h1>
        <p className="kiosk__intro">
          Pegá la credencial que te dieron en el panel de administración al dar de alta este kiosco.
          Se guarda solo en este dispositivo.
        </p>
        <form className="kiosk__form" onSubmit={submit} noValidate>
          <Input
            label="Credencial del kiosco"
            type="password"
            autoComplete="off"
            value={draft}
            error={error}
            onChange={(e) => setDraft(e.target.value)}
          />
          <Button type="submit">Guardar y mostrar el QR</Button>
        </form>
      </main>
    );
  }

  return (
    <main className="kiosk">
      <h1 className="text-title-lg">Ascensor {ascensor}</h1>
      <KioskQr
        key={secret}
        secret={secret}
        elevator={ascensor}
        unauthorizedAction={<Button onClick={() => save(null)}>Borrar credencial</Button>}
        footer={
          <ConfirmAction
            label="Borrar credencial"
            question="¿Borrar la credencial de este dispositivo?"
            confirmLabel="Sí, borrar"
            onConfirm={() => save(null)}
          />
        }
      />
    </main>
  );
}
