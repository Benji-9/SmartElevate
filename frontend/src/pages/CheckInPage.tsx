import { Link } from 'react-router';
import { ScreenHeader } from '../components/ScreenHeader';

export function CheckInPage() {
  return (
    <>
      <ScreenHeader title="Check-in" />
      <p className="page-placeholder">Próximamente: escaneá el QR de la pantalla del ascensor.</p>
      <Link to="/check-in/codigo">Ingresar código manualmente</Link>
    </>
  );
}
