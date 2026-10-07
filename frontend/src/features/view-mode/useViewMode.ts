import { createContext, useContext } from 'react';

/** Modos de vista de VIEW-MODES.md: la app a todo el ancho o dentro de un marco de celular. */
export type ViewMode = 'pantalla' | 'telefono';

export type ViewModeState = {
  /** Modo elegido; el marco se dibuja solo si además `canToggle`. */
  mode: ViewMode;
  setMode: (mode: ViewMode) => void;
  /** Ventana de 1024 px o más y fuera de `/admin`: hay selector y puede haber marco. */
  canToggle: boolean;
};

export const ViewModeContext = createContext<ViewModeState | null>(null);

export function useViewMode(): ViewModeState {
  const state = useContext(ViewModeContext);
  if (!state) throw new Error('useViewMode tiene que usarse dentro de <ViewModeProvider>');
  return state;
}
