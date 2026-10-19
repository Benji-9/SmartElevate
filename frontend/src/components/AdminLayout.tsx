import { NavLink, Outlet } from 'react-router';
import { useResource } from '../hooks/useResource';
import { getKiosks } from '../services/api';
import './AdminLayout.css';

/**
 * Panel de administración, solo de escritorio (VIEW-MODES.md §7): con el contenedor de
 * menos de 900 px se muestra un aviso en lugar del panel.
 */
export function AdminLayout() {
  const kiosks = useResource(getKiosks);
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
              <li>
                <NavLink to="/admin/prioritarios" className="admin__link">
                  Gestión de usuarios
                </NavLink>
              </li>
              <li>
                <NavLink to="/admin/kioscos" className="admin__link">
                  Kioscos
                </NavLink>
              </li>
              <li>
                <NavLink to="/admin/ascensores" className="admin__link">
                  Ascensores
                </NavLink>
              </li>
              {/* El flag lo manda el servidor (APP_KIOSK_SIMULATOR_ENABLED); sin respuesta, no va. */}
              {kiosks.data?.simulatorEnabled && (
                <li>
                  <NavLink to="/admin/simulador" className="admin__link">
                    Simulador de kiosco
                  </NavLink>
                </li>
              )}
              {/* Turnos, Núcleos y Reportes se suman cuando existan (#158). */}
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
