import { Link, Navigate, Outlet, useLocation } from 'react-router';
import { useSession } from '../../hooks/useSession';
import type { Role } from '../../types/api';

type RequireSessionProps = {
  /** Rol necesario según `/users/me`. Es solo para la UI: la autorización real la hace el backend. */
  role?: Role;
};

/** Rutas privadas: sin sesión manda a `/login` recordando a dónde se quería ir. */
export function RequireSession({ role }: RequireSessionProps) {
  const { user, loading } = useSession();
  const location = useLocation();

  if (loading) {
    // Mismo contenedor que las pantallas, así el texto no queda pegado al borde.
    return (
      <div className="screen">
        <main className="screen__main">
          <p role="status" className="page-placeholder">
            Cargando…
          </p>
        </main>
      </div>
    );
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  if (role && user.role !== role) {
    return (
      <div className="screen">
        <main className="screen__main">
          <h1 className="text-heading">No tenés acceso a esta sección</h1>
          <p>Es solo para administradores.</p>
          <Link to="/">Volver al inicio</Link>
        </main>
      </div>
    );
  }
  return <Outlet />;
}
