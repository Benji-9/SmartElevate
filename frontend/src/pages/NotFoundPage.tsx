import { Link } from 'react-router';
import '../components/Button.css';
import { Mascot } from '../components/Mascot';
import { ScreenHeader } from '../components/ScreenHeader';

export function NotFoundPage() {
  return (
    <>
      <ScreenHeader title="Página no encontrada" />
      <div className="empty-state">
        <Mascot pose="piensa" size={120} />
        <p className="page-placeholder">
          Uy, esta página no existe o cambió de lugar. Revisá la dirección o volvé al inicio.
        </p>
      </div>
      <Link to="/" className="button button--primary">
        Ir al inicio
      </Link>
    </>
  );
}
