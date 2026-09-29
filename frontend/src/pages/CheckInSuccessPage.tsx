import { Link } from 'react-router';

export function CheckInSuccessPage() {
  return (
    <section>
      <h1>Viaje registrado</h1>
      <p className="page-placeholder">Próximamente: resultado del check-in y encuesta de espera.</p>
      <Link to="/">Volver al inicio</Link>
    </section>
  );
}
