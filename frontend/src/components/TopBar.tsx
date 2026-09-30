import { NavLink } from 'react-router';
import logo from '../assets/smartelevate-logo-horizontal.svg';
import { useSession } from '../hooks/useSession';
import { Avatar } from './Avatar';
import { navItems } from './navItems';
import './TopBar.css';

/**
 * Navegación del layout de Pantalla (VIEW-MODES.md §5). Se renderiza junto con la BottomNav
 * y el container query oculta la que no corresponde (`display: none`), así nunca hay dos.
 */
export function TopBar() {
  const { user } = useSession();
  return (
    <header className="topbar only-desktop">
      <img className="topbar__logo" src={logo} alt="SmartElevate" width={131} height={32} />
      <nav className="topbar__nav" aria-label="Principal">
        <ul>
          {navItems.map((item) => (
            <li key={item.to}>
              <NavLink to={item.to} end={item.end} className="topbar__tab text-body-sm-strong">
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      {user && (
        <p className="topbar__user text-body-sm-strong">
          <Avatar name={user.fullName} className="topbar__avatar" />
          {user.fullName}
        </p>
      )}
    </header>
  );
}
