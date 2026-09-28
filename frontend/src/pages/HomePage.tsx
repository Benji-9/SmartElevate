import { ApiStatusBadge } from '../components/ApiStatusBadge';

export function HomePage() {
  return (
    <section>
      <h1>SmartElevate</h1>
      <p>Reservá tu turno de ascensor y evitá las filas en el campus.</p>
      <ApiStatusBadge />
    </section>
  );
}
