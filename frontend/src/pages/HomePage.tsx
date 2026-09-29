import { ApiStatusBadge } from '../components/ApiStatusBadge';

export function HomePage() {
  return (
    <section>
      <h1>Inicio</h1>
      <p className="page-placeholder">
        Próximamente: tu turno activo y la congestión de cada núcleo.
      </p>
      <ApiStatusBadge />
    </section>
  );
}
