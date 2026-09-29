import { useState } from 'react';
import { Button } from '../components/Button';
import { ScreenHeader } from '../components/ScreenHeader';
import { Switch } from '../components/Switch';
import { errorMessage, useResource } from '../hooks/useResource';
import { getNotificationPreferences, saveNotificationPreferences } from '../services/api';
import type { NotificationPreferences } from '../types/pending';
import './NotificationsPage.css';

const options: { key: keyof NotificationPreferences; label: string; description: string }[] = [
  {
    key: 'departureReminder',
    label: 'Recordatorio de salida',
    description: 'Te avisamos un rato antes de que salga tu ascensor.',
  },
  {
    key: 'spotReleased',
    label: 'Lugar liberado',
    description: 'Si estás en lista de espera y se libera un lugar en la salida.',
  },
  {
    key: 'delayCancellation',
    label: 'Cancelación por demora',
    description: 'Si tu salida se demora y podés cancelar sin que cuente como falta.',
  },
  {
    key: 'priorityAccess',
    label: 'Acceso prioritario',
    description: 'Cuando se aprueba o vence tu prioridad.',
  },
];

const DENIED =
  'Las notificaciones están bloqueadas en este navegador. Para activarlas, habilitalas en la configuración del sitio (el ícono junto a la dirección) y volvé a intentar.';
const UNSUPPORTED =
  'Este navegador no admite notificaciones. Probá desde otro navegador actualizado.';

/** Pide permiso solo si todavía no se respondió. Devuelve el mensaje si no se puede avisar. */
async function notificationBlocker(): Promise<string | null> {
  if (typeof Notification === 'undefined') return UNSUPPORTED;
  const permission =
    Notification.permission === 'default'
      ? await Notification.requestPermission()
      : Notification.permission;
  return permission === 'granted' ? null : DENIED;
}

export function NotificationsPage() {
  const { data, error, loading, reload } = useResource(getNotificationPreferences);
  // Cambios locales (optimistas) sobre lo que vino del servidor.
  const [edited, setEdited] = useState<NotificationPreferences | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const prefs = edited ?? data;

  async function toggle(key: keyof NotificationPreferences, value: boolean) {
    if (!prefs) return;
    setMessage(null);
    if (value) {
      const blocker = await notificationBlocker();
      if (blocker) {
        setMessage(blocker);
        return;
      }
    }
    setEdited({ ...prefs, [key]: value });
    try {
      await saveNotificationPreferences({ ...prefs, [key]: value });
    } catch (saveError) {
      setEdited((current) => current && { ...current, [key]: !value });
      setMessage(errorMessage(saveError));
    }
  }

  let content;
  if (error) {
    content = (
      <div className="notifications__error">
        <p role="alert" className="notifications__alert">
          {error}
        </p>
        <Button variant="secondary" onClick={reload}>
          Reintentar
        </Button>
      </div>
    );
  } else if (loading || !prefs) {
    content = (
      <p role="status" className="page-placeholder">
        Cargando tus preferencias…
      </p>
    );
  } else {
    content = (
      <>
        <p className="notifications__intro">Elegí qué avisos querés recibir.</p>
        {message && (
          <p role="alert" className="notifications__alert">
            {message}
          </p>
        )}
        <div role="group" aria-label="Avisos">
          {options.map((option) => (
            <Switch
              key={option.key}
              label={option.label}
              description={option.description}
              checked={prefs[option.key]}
              onChange={(value) => toggle(option.key, value)}
            />
          ))}
        </div>
      </>
    );
  }

  return (
    <>
      <ScreenHeader title="Notificaciones" backTo="/perfil" />
      {content}
    </>
  );
}
