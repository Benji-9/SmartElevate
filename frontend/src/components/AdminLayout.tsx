import { NavLink, Outlet } from 'react-router';
import './AdminLayout.css';

/**
 * Panel de administración, solo de escritorio (VIEW-MODES.md §7): con el contenedor de
 * menos de 900 px se muestra un aviso en lugar del panel.
 */
export function AdminLayout() {
  return (
    <div className="admin-shell">
      <div className="admin">
        <aside className="admin__sidebar">
          {/* El logo en modo claro es oscuro y no contrasta sobre el sidebar: va el texto. */}
          <p className="admin__brand">SmartElevate</p>
          <p className="admin__subtitle">Panel UADE</p>
          <nav aria-label="Administración">
            <ul>
              <li>
                <NavLink to="/admin" end className="admin__link">
                  Dashboard
                </NavLink>
              </li>
              {/* Turnos, Núcleos, Usuarios prioritarios y Reportes se suman cuando existan (#158). */}
            </ul>
          </nav>
        </aside>
        <main className="admin__main">
          <Outlet />
        </main>
      </div>
      <div className="admin-narrow">
        <h1 className="text-heading">El panel de administración es para computadora</h1>
        <p>Abrilo desde una pantalla más grande para ver el detalle de congestión.</p>
      </div>
    </div>
  );
}
