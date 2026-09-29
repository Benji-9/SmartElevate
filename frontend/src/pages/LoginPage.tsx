import { Link } from 'react-router';

export function LoginPage() {
  return (
    <section>
      <h1>Bienvenido a SmartElevate</h1>
      <p className="page-placeholder">Reservá tu turno de ascensor y llegá a tiempo a clase.</p>
      <p>
        ¿No tenés cuenta? <Link to="/registro">Registrate</Link>
      </p>
    </section>
  );
}
