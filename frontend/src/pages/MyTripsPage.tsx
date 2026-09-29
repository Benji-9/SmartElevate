import { ScreenHeader } from '../components/ScreenHeader';

export function MyTripsPage() {
  return (
    <>
      <ScreenHeader title="Mis viajes" backTo="/perfil" />
      <p className="page-placeholder">Próximamente: tus turnos anteriores y su resultado.</p>
    </>
  );
}
