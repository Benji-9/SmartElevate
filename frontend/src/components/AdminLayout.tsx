import { NavLink, Outlet } from 'react-router';
import './AdminLayout.css';

// Solo el Dashboard tiene diseño (Figma 09); el resto del menú queda para más adelante.
const upcoming = ['Turnos', 'Núcleos y ascensores', 'Usuarios prioritarios', 'Reportes'];

/** Panel de administración (desktop): sidebar + contenido. */
export function AdminLayout() {
  return (
    <div className="admin">
      <aside className="admin__sidebar">
        <p className="admin__brand">SmartElevate</p>
        <p className="admin__subtitle">Panel UADE</p>
        <nav aria-label="Administración">
          <ul>
            <li>
              <NavLink to="/admin" end className="admin__link">
                Dashboard
              </NavLink>
            </li>
            {upcoming.map((label) => (
              <li key={label}>
                <span className="admin__link admin__link--disabled" aria-disabled="true">
                  {label}
                  <span className="admin__soon">Próximamente</span>
                </span>
              </li>
            ))}
          </ul>
        </nav>
      </aside>
      <main className="admin__main">
        <Outlet />
      </main>
    </div>
  );
}
