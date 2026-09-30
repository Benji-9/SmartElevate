import { Outlet } from 'react-router';
import { BottomNav } from './BottomNav';
import { TopBar } from './TopBar';
import './Layouts.css';

/**
 * Pantallas principales: contenido + navegación. En el layout móvil va la BottomNav y en el de
 * Pantalla el TopBar (VIEW-MODES.md §5); se renderizan las dos y el container query elige.
 */
export function TabLayout() {
  return (
    <div className="screen">
      <TopBar />
      <main className="screen__main page">
        <Outlet />
      </main>
      <BottomNav />
    </div>
  );
}

/**
 * Pantallas de un flujo (login, reserva, check-in…): sin navegación inferior.
 * `topBar` suma el TopBar en el layout de Pantalla (Reservar y Check-in, VIEW-MODES.md §5–§6).
 * `bleed` saca el pad para que la pantalla (la cámara del check-in) llene la columna.
 */
export function ScreenLayout({
  bleed = false,
  topBar = false,
}: {
  bleed?: boolean;
  topBar?: boolean;
}) {
  const mainClass = ['screen__main', bleed && 'screen__main--bleed', topBar && 'page']
    .filter(Boolean)
    .join(' ');
  return (
    <div className="screen">
      {topBar && <TopBar />}
      <main className={mainClass}>
        <Outlet />
      </main>
    </div>
  );
}
