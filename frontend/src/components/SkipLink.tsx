import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router';
import './SkipLink.css';

/**
 * "Saltar al contenido" (visible solo con foco) y, al cambiar de ruta, lleva el foco al `<main id="main">`
 * para que teclado y lectores de pantalla no queden en el enlace de la pantalla anterior.
 */
export function SkipLink() {
  const { pathname } = useLocation();
  const first = useRef(true);

  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    document.getElementById('main')?.focus({ preventScroll: true });
  }, [pathname]);

  return (
    <a href="#main" className="skip-link" onClick={focusMain}>
      Saltar al contenido
    </a>
  );
}

// Con HashRouter/BrowserRouter el ancla sola no mueve el foco en todos los navegadores.
function focusMain(event: React.MouseEvent) {
  event.preventDefault();
  document.getElementById('main')?.focus();
}
