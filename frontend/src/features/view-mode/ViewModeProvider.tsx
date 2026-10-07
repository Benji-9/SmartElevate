import { useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from 'react';
import { useLocation } from 'react-router';
import { ViewModeContext, type ViewMode } from './useViewMode';

const KEY = 'se:viewMode';
/** Ancho de ventana desde el que hay selector (no es un corte de layout: eso va por container). */
const MIN_WIDTH = 1024;

const isMode = (value: string | null): value is ViewMode =>
  value === 'pantalla' || value === 'telefono';

/** `?vista=` → `localStorage` → `pantalla` (VIEW-MODES.md §2). */
function initialMode(search: string): ViewMode {
  const param = new URLSearchParams(search).get('vista');
  if (isMode(param)) return param;
  try {
    const saved = localStorage.getItem(KEY);
    if (isMode(saved)) return saved;
  } catch {
    // Sin localStorage (bloqueado o modo privado): queda el default.
  }
  return 'pantalla';
}

function subscribeResize(onChange: () => void) {
  window.addEventListener('resize', onChange);
  return () => window.removeEventListener('resize', onChange);
}

const isWide = () => window.innerWidth >= MIN_WIDTH;

/**
 * Modo de vista Pantalla / Teléfono. En `/admin` no hay selector y, como el marco solo se
 * dibuja con `canToggle`, se ve en Pantalla sin tocar el modo elegido: al salir vuelve solo.
 */
export function ViewModeProvider({ children }: { children: ReactNode }) {
  const { pathname, search } = useLocation();
  const [mode, setMode] = useState(() => initialMode(search));
  const wide = useSyncExternalStore(subscribeResize, isWide);
  const canToggle = wide && !/^\/admin(\/|$)/.test(pathname);

  // También guarda el `?vista=` de la URL: el link de la demo queda aplicado al recargar.
  useEffect(() => {
    try {
      localStorage.setItem(KEY, mode);
    } catch {
      // Sin localStorage el modo vale mientras dure la pestaña.
    }
  }, [mode]);

  const value = useMemo(() => ({ mode, setMode, canToggle }), [mode, canToggle]);
  return <ViewModeContext value={value}>{children}</ViewModeContext>;
}
