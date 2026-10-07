import { Link } from 'react-router';
import { CONTACT_EMAIL, LegalFooter } from '../components/LegalFooter';
import { ScreenHeader } from '../components/ScreenHeader';
import './LegalPages.css';

/**
 * Términos de uso (SCREENS.md §Páginas legales). Los valores de las reglas (cupo, faltas,
 * ventanas) no se escriben acá: salen de la configuración y la app los muestra donde aplican.
 */
export function TermsPage() {
  return (
    <article className="legal">
      <ScreenHeader title="Términos de uso" backTo="/login" />
      <p className="legal__updated">Última actualización: octubre de 2026</p>

      <p>
        Estos términos regulan el uso de SmartElevate, un proyecto académico de estudiantes de UADE
        para ordenar el uso de los ascensores del campus con turnos. Al crear una cuenta los
        aceptás, junto con la <Link to="/privacidad">Política de privacidad</Link>.
      </p>

      <section aria-labelledby="terms-service">
        <h2 id="terms-service">1. El servicio</h2>
        <p>
          SmartElevate permite reservar un lugar en una salida de ascensor, hacer check-in con el QR
          de la cabina y ver la congestión de cada núcleo. Es gratuito y se ofrece como prototipo
          (MVP) en el marco de una materia: no es un servicio oficial de UADE, puede cambiar,
          interrumpirse o terminar al finalizar el proyecto. No reemplaza las indicaciones del
          personal de la universidad ni las señales de seguridad del edificio.
        </p>
      </section>

      <section aria-labelledby="terms-account">
        <h2 id="terms-account">2. Tu cuenta</h2>
        <ul>
          <li>Necesitás un email institucional @uade.edu.ar verificado y tu legajo.</li>
          <li>
            Los datos que cargás tienen que ser verdaderos. El tipo de usuario es una declaración:
            ser docente lo valida un administrador.
          </li>
          <li>
            La cuenta es personal: no la compartas ni reserves para otra persona. Cuidá tu
            contraseña y avisanos si creés que alguien entró a tu cuenta.
          </li>
          <li>
            Si sos menor de edad, usá SmartElevate con la autorización de tu madre, padre o tutor.
          </li>
        </ul>
      </section>

      <section aria-labelledby="terms-turns">
        <h2 id="terms-turns">3. Turnos y check-in</h2>
        <ul>
          <li>
            Cada salida tiene un cupo máximo de personas. Las reglas vigentes (cuántos turnos podés
            tener, con cuánta anticipación reservar y hasta cuándo cancelar) se muestran en la app.
          </li>
          <li>
            Si no vas a usar un turno, cancelalo a tiempo para liberar el lugar. No presentarte o
            cancelar fuera de término cuenta como falta, y acumular faltas puede suspender por un
            tiempo la posibilidad de reservar.
          </li>
          <li>
            El check-in se hace escaneando el QR que aparece dentro de la cabina, que cambia cada
            poco tiempo. No compartas ni reutilices códigos.
          </li>
          <li>
            Un turno no garantiza un horario exacto: el ascensor puede demorarse, fallar o quedar
            fuera de servicio.
          </li>
        </ul>
      </section>

      <section aria-labelledby="terms-priority">
        <h2 id="terms-priority">4. Acceso prioritario</h2>
        <p>
          Las personas con movilidad reducida y los docentes validados tienen prioridad y, en
          algunos núcleos, ascensores dedicados. La prioridad se pide desde el perfil con un
          certificado, que revisa el equipo y se borra al resolver la solicitud. Presentar
          documentación falsa o ajena es motivo de baja de la cuenta, sin perjuicio de otras
          responsabilidades.
        </p>
      </section>

      <section aria-labelledby="terms-conduct">
        <h2 id="terms-conduct">5. Uso aceptable</h2>
        <p>No está permitido:</p>
        <ul>
          <li>Reservar turnos que no vas a usar o acaparar lugares.</li>
          <li>Crear cuentas falsas o usar la de otra persona.</li>
          <li>
            Automatizar reservas, intentar acceder a datos de otros usuarios o vulnerar la seguridad
            del sistema.
          </li>
          <li>Usar el servicio con fines comerciales o contrarios a la ley.</li>
        </ul>
        <p>
          Ante un uso indebido podemos suspender o dar de baja la cuenta. Si encontrás una falla de
          seguridad, avisanos a <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a> sin
          aprovecharla.
        </p>
      </section>

      <section aria-labelledby="terms-liability">
        <h2 id="terms-liability">6. Responsabilidad</h2>
        <p>
          Al ser un proyecto académico, SmartElevate se ofrece «tal como está», sin garantía de
          disponibilidad permanente ni de que esté libre de errores. En la medida en que la ley lo
          permita, el equipo no responde por llegadas tarde, demoras o daños derivados de usar o no
          poder usar el servicio. Nada de esto limita los derechos que te reconoce la ley.
        </p>
      </section>

      <section aria-labelledby="terms-ip">
        <h2 id="terms-ip">7. Propiedad intelectual</h2>
        <p>
          El código, el diseño y la marca SmartElevate pertenecen a sus autores. El nombre y el logo
          de UADE pertenecen a la universidad y se usan solo para identificar el contexto del
          proyecto.
        </p>
      </section>

      <section aria-labelledby="terms-changes">
        <h2 id="terms-changes">8. Cambios, ley aplicable y contacto</h2>
        <p>
          Podemos actualizar estos términos; si el cambio es importante, te lo avisamos en la app.
          Se rigen por las leyes de la República Argentina y, ante cualquier conflicto, son
          competentes los tribunales de la Ciudad Autónoma de Buenos Aires. Para consultas
          escribinos a <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
        </p>
      </section>

      <LegalFooter />
    </article>
  );
}
