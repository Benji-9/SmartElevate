import { useLocation, useNavigate } from 'react-router';
import './ScreenHeader.css';

type ScreenHeaderProps = {
  title: string;
  /** A dónde ir si la pantalla se abrió directo (sin historial). */
  backTo?: string;
};

/** Encabezado de pantalla con botón "Volver" y el título como `h1`. */
export function ScreenHeader({ title, backTo = '/' }: ScreenHeaderProps) {
  const navigate = useNavigate();
  const location = useLocation();

  function goBack() {
    // `default` es la key de la primera entrada del historial: no hay a dónde volver.
    if (location.key === 'default') {
      navigate(backTo);
    } else {
      navigate(-1);
    }
  }

  return (
    <header className="screen-header">
      <button type="button" className="screen-header__back" aria-label="Volver" onClick={goBack}>
        <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
          <path
            d="M15 5l-7 7 7 7"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>
      <h1 className="screen-header__title">{title}</h1>
    </header>
  );
}
