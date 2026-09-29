import { ScreenHeader } from '../components/ScreenHeader';

export function RegisterPage() {
  return (
    <>
      <ScreenHeader title="Crear cuenta" backTo="/login" />
      <p className="page-placeholder">Usá tu email institucional y tu legajo.</p>
    </>
  );
}
