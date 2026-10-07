import { Link } from 'react-router';
import './LegalFooter.css';

/** Contacto del proyecto para consultas y derechos sobre datos personales. */
export const CONTACT_EMAIL = 'smartelevate.uade@gmail.com';

/** Links legales al pie (SCREENS.md §Footer legal). */
export function LegalFooter() {
  return (
    <footer className="legal-footer">
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
