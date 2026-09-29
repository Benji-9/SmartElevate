import { ScreenHeader } from '../components/ScreenHeader';

export function NotificationsPage() {
  return (
    <>
      <ScreenHeader title="Notificaciones" backTo="/perfil" />
      <p className="page-placeholder">Próximamente: elegí qué avisos querés recibir.</p>
    </>
  );
}
