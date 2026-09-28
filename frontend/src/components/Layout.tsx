import { NavLink, Outlet } from 'react-router';

const links = [
  { to: '/', label: 'Inicio', end: true },
  { to: '/solicitar-turno', label: 'Solicitar turno' },
  { to: '/mis-turnos', label: 'Mis turnos' },
  { to: '/ascensores', label: 'Estado de ascensores' },
];

export function Layout() {
  return (
    <div className="app">
      <header className="app-header">
        <span className="brand">SmartElevate</span>
        <nav aria-label="Principal">
          <ul>
            {links.map((link) => (
              <li key={link.to}>
                <NavLink to={link.to} end={link.end}>
                  {link.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
