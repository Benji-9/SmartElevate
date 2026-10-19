import { Link } from 'react-router';
import uadeLogo from '../assets/uade-logo.svg';
import './LegalFooter.css';

/** Contacto del proyecto para consultas y derechos sobre datos personales. */
export const CONTACT_EMAIL = 'smartelevate.uade@gmail.com';

/**
 * Links legales al pie (SCREENS.md §Footer legal). `withUadeLogo` suma el logotipo de UADE arriba
 * (Login y Perfil).
 */
export function LegalFooter({ withUadeLogo = false }: { withUadeLogo?: boolean }) {
  return (
    <footer className="legal-footer">
      {withUadeLogo && (
        <img className="legal-footer__uade" src={uadeLogo} alt="UADE" width={86} height={30} />
      )}
      <nav aria-label="Información legal">
        <ul className="legal-footer__links">
          <li>
            <Link to="/privacidad">Privacidad</Link>
          </li>
          <li>
            <Link to="/terminos">Términos de uso</Link>
          </li>
          <li>
            <a href={`mailto:${CONTACT_EMAIL}`}>Contacto</a>
          </li>
        </ul>
      </nav>
      <p>SmartElevate · Proyecto académico de estudiantes de UADE</p>
    </footer>
  );
}
