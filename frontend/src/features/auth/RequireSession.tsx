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
    return (
      <p role="status" className="page-placeholder">
        Cargando…
      </p>
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
