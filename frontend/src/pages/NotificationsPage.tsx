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
  // "Lugar liberado" (`spotReleased`) queda oculto: avisa de la lista de espera, que la UI todavía
  // no tiene (docs/reglas/turnos.md §7, fuera de la Fase 2 #105). Volver a mostrarlo cuando se
  // implemente la lista de espera (#157). El valor del servidor se reenvía sin cambios al guardar.
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
        <p className="notifications__intro">
          Elegí qué avisos querés recibir. Te pedimos permiso del navegador recién cuando actives el
          primero.
        </p>
        {message && (
          <p role="alert" className="notifications__alert">
            {message}
          </p>
        )}
        <div role="group" aria-label="Avisos" className="notifications__list">
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
        <p className="notifications__intro">
          Los avisos llegan como notificaciones del navegador. Podés desactivarlos cuando quieras.
        </p>
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
