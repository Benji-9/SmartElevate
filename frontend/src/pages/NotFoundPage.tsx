import { Link } from 'react-router';
import '../components/Button.css';
import { ScreenHeader } from '../components/ScreenHeader';

export function NotFoundPage() {
  return (
    <>
      <ScreenHeader title="Página no encontrada" />
      <p className="page-placeholder">
        La dirección no existe o cambió. Revisala o volvé al inicio.
      </p>
      <Link to="/" className="button button--primary">
        Ir al inicio
      </Link>
    </>
  );
}
