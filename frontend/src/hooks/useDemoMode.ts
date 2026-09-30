import { useLocation } from 'react-router';

const KEY = 'se:demo';

/**
 * Ayudas de demo (VIEW-MODES.md §8). `?demo=1` las prende y `?demo=0` las apaga; el flag queda
 * en sessionStorage para conservarse al navegar dentro de la app.
 */
export function useDemoMode(): boolean {
  const flag = new URLSearchParams(useLocation().search).get('demo');
  try {
    if (flag === '1') sessionStorage.setItem(KEY, '1');
    else if (flag === '0') sessionStorage.removeItem(KEY);
    return sessionStorage.getItem(KEY) === '1';
  } catch {
    // Sin sessionStorage (p. ej. bloqueado) vale solo mientras el param esté en la URL.
    return flag === '1';
  }
}
