import { useEffect, type ReactNode } from 'react';
import { useLocation } from 'react-router';
import { useViewMode } from './useViewMode';
import { ViewModeToggle } from './ViewModeToggle';
import './view-mode.css';

/** Marco con bisel: el alto total que tiene que entrar en la ventana. */
const PHONE_H = 844 + 2 * 10;
/** Pad del escenario (24 arriba y abajo). */
const STAGE_PAD = 48;

/** Escala el marco para que entre en laptops de poca altura (nunca agranda). */
function useFitScale(active: boolean) {
  useEffect(() => {
    const set = () => {
      const scale = active ? Math.min(1, (window.innerHeight - STAGE_PAD) / PHONE_H) : 1;
      document.documentElement.style.setProperty('--phone-scale', String(scale));
    };
    set();
    window.addEventListener('resize', set);
    return () => window.removeEventListener('resize', set);
  }, [active]);
}

/** Barra de estado decorativa del marco: `dark` (texto blanco) sobre la cámara del check-in. */
function StatusBar({ tone }: { tone: 'light' | 'dark' }) {
  return (
    <div className={`status-bar status-bar--${tone}`} aria-hidden="true">
      <span className="text-body-sm-strong">9:41</span>
      <svg width="54" height="12" viewBox="0 0 54 12" fill="currentColor">
        {/* Señal */}
        <rect x="0" y="8" width="3" height="4" rx="1" />
        <rect x="4.5" y="6" width="3" height="6" rx="1" />
        <rect x="9" y="3" width="3" height="9" rx="1" />
        <rect x="13.5" y="0" width="3" height="12" rx="1" />
        {/* Wifi */}
        <path d="M27 11.5 24.6 9a3.4 3.4 0 0 1 4.8 0Zm-4-4a5.7 5.7 0 0 1 8 0l1.4-1.4a7.7 7.7 0 0 0-10.8 0Z" />
        {/* Batería */}
        <rect x="35.5" y="1" width="16" height="10" rx="3" fill="none" stroke="currentColor" />
        <rect x="37.5" y="3" width="12" height="6" rx="1.5" />
        <rect x="52.5" y="4" width="1.5" height="4" rx="0.75" />
      </svg>
    </div>
  );
}

/**
 * Escenario de la app (VIEW-MODES.md §4). Los dos modos usan el mismo árbol y solo cambia
 * `data-mode`: así cambiar de modo no desmonta la pantalla ni borra los formularios.
 */
export function DeviceStage({ children }: { children: ReactNode }) {
  const { mode, canToggle } = useViewMode();
  const { pathname } = useLocation();
  const framed = canToggle && mode === 'telefono';
  useFitScale(framed);
  const tone = pathname === '/check-in' || pathname === '/check-in/codigo' ? 'dark' : 'light';

  return (
    <div className="stage" data-mode={framed ? 'telefono' : 'pantalla'}>
      <div className="phone-slot">
        <div className="phone">
          <div className="phone-screen" data-tone={tone}>
            {framed && <StatusBar tone={tone} />}
            <div className="app-scroll">
              <div className="app-shell">{children}</div>
            </div>
          </div>
        </div>
      </div>
      {canToggle && <ViewModeToggle />}
    </div>
  );
}
