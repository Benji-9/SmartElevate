import { useState } from 'react';
import { Button } from '../components/Button';
import { ScreenHeader } from '../components/ScreenHeader';
import { Switch } from '../components/Switch';
import { errorMessage, useResource } from '../hooks/useResource';
import { getNotificationPreferences, saveNotificationPreferences } from '../services/api';
import type { NotificationPreferences } from '../types/api';
import './NotificationsPage.css';

// Los avisos salen por email desde el servidor (#221): no hace falta permiso del navegador. "Lugar
// liberado" no existe: sin lista de espera (fuera del MVP, turnos.md §7) no hay a quién avisarle.
// Tampoco hay aviso de prioridad: el mail no lleva datos de prioridad ni de salud.
const options: { key: keyof NotificationPreferences; label: string; description: string }[] = [
  {
    key: 'departureReminder',
    label: 'Recordatorio de salida',
    description: 'Te mandamos un email un rato antes de que salga tu ascensor.',
  },
  {
    key: 'elevatorOutOfService',
    label: 'Ascensor fuera de servicio',
    description:
      'Si cancelamos tu turno porque el ascensor sale de servicio (no cuenta como falta).',
  },
];

export function NotificationsPage() {
  const { data, error, loading, reload } = useResource(getNotificationPreferences);
  // Cambios locales (optimistas) sobre lo que vino del servidor.
  const [edited, setEdited] = useState<NotificationPreferences | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const prefs = edited ?? data;

  async function toggle(key: keyof NotificationPreferences, value: boolean) {
    if (!prefs) return;
    setMessage(null);
    setSaved(false);
    setEdited({ ...prefs, [key]: value });
    try {
      await saveNotificationPreferences({ ...prefs, [key]: value });
      setSaved(true);
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
          Elegí qué avisos querés recibir. Te escribimos solo cuando hace falta.
        </p>
        {message && (
          <p role="alert" className="notifications__alert">
            {message}
          </p>
        )}
        {/* Siempre montado (vacío) para que el lector de pantalla anuncie el cambio (#159). */}
        <p role="status" className="notifications__saved">
          {saved && 'Guardamos tu preferencia.'}
        </p>
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
          Los mails no incluyen datos de salud ni de prioridad. Podés desactivarlos cuando quieras.
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
