import { Outlet } from 'react-router';
import { BottomNav } from './BottomNav';
import './Layouts.css';

/** Pantallas principales de la app móvil: contenido + navegación inferior. */
export function TabLayout() {
  return (
    <div className="screen">
      <main className="screen__main">
        <Outlet />
      </main>
      <BottomNav />
    </div>
  );
}

/** Pantallas de un flujo (login, reserva, check-in…): sin navegación inferior. */
export function ScreenLayout() {
  return (
    <div className="screen">
      <main className="screen__main">
        <Outlet />
      </main>
    </div>
  );
}
