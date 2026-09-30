import type { ReactNode } from 'react';
import { NavLink } from 'react-router';
import { navItems } from './navItems';
import './BottomNav.css';

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="22"
      height="22"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

/** Navegación principal del layout móvil (Figma: BottomNav). En Pantalla la reemplaza el TopBar. */
export function BottomNav() {
  return (
    <nav className="bottom-nav only-mobile" aria-label="Principal">
      <ul>
        {navItems.map((item) => (
          <li key={item.to}>
            <NavLink to={item.to} end={item.end} className="bottom-nav__link">
              <Icon>{item.icon}</Icon>
              <span>{item.label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
