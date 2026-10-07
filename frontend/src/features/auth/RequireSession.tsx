import { Navigate, Outlet, useLocation } from 'react-router';
import { useSession } from '../../hooks/useSession';
import type { Role } from '../../types/pending';

type RequireSessionProps = {
  /** Rol necesario según `/me`. Es solo para la UI: la autorización real la hace el backend. */
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
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
