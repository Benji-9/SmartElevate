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

/**
 * Pantallas de un flujo (login, reserva, check-in…): sin navegación inferior.
 * `bleed` saca el pad para que la pantalla (la cámara del check-in) llene la columna.
 */
export function ScreenLayout({ bleed = false }: { bleed?: boolean }) {
  return (
    <div className="screen">
      <main className={bleed ? 'screen__main screen__main--bleed' : 'screen__main'}>
        <Outlet />
      </main>
    </div>
  );
}
