import { Link, Navigate, Outlet, useLocation } from 'react-router';
import { Skeleton } from '../../components/Skeleton';
import { TopBar } from '../../components/TopBar';
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
    // El esqueleto de la pantalla (TopBar en Pantalla y bloques de carga) mientras se recupera la
    // sesión: la app no arranca con un "Cargando…" suelto sobre blanco.
    return (
      <div className="screen">
        <TopBar />
        <main id="main" tabIndex={-1} className="screen__main page">
          <Skeleton label="Cargando…" rows={3} height={96} />
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
        <main id="main" tabIndex={-1} className="screen__main">
          <h1 className="text-heading">No tenés acceso a esta sección</h1>
          <p>Es solo para administradores.</p>
          <Link to="/">Volver al inicio</Link>
        </main>
      </div>
    );
  }
  return <Outlet />;
}
