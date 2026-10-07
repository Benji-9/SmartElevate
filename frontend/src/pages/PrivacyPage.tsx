import { Link } from 'react-router';
import { CONTACT_EMAIL, LegalFooter } from '../components/LegalFooter';
import { ScreenHeader } from '../components/ScreenHeader';
import './LegalPages.css';

const mail = <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>;

/**
 * Política de privacidad (SCREENS.md §Páginas legales). Pensada para la Ley 25.326 y normas de
 * la AAIP, con los derechos del RGPD. No es asesoramiento legal: validar con la cátedra (#24).
 */
export function PrivacyPage() {
  return (
    <article className="legal">
      <ScreenHeader title="Política de privacidad" backTo="/login" />
      <p className="legal__updated">Última actualización: octubre de 2026</p>

      <p>
        SmartElevate es un proyecto académico de estudiantes de UADE para reservar turnos de
        ascensor en el campus. Esta política explica qué datos personales usamos, para qué, con
        quién los compartimos y cómo podés ejercer tus derechos. Está pensada para cumplir la{' '}
        <strong>Ley 25.326 de Protección de los Datos Personales</strong> y su Decreto reglamentario
        1558/2001, las normas de la Agencia de Acceso a la Información Pública (AAIP) y el Convenio
        108 del Consejo de Europa y su Protocolo 108+ (Leyes 27.483 y 27.699). Si estás en la Unión
        Europea, también te reconocemos los derechos del Reglamento General de Protección de Datos
        (RGPD).
      </p>

      <section aria-labelledby="privacy-responsible">
        <h2 id="privacy-responsible">1. Quién es responsable</h2>
        <p>
          El responsable de la base de datos es el equipo de estudiantes que desarrolla
          SmartElevate. SmartElevate no es un servicio oficial de UADE ni reemplaza sus canales
          institucionales. Para cualquier consulta sobre tus datos escribinos a {mail}.
        </p>
      </section>

      <section aria-labelledby="privacy-data">
        <h2 id="privacy-data">2. Qué datos usamos</h2>
        <ul>
          <li>
            <strong>Cuenta:</strong> nombre y apellido, email institucional (@uade.edu.ar), legajo,
            el tipo de usuario que declarás (estudiante o docente) y tu contraseña, que guardamos
            cifrada con un algoritmo de hash y nunca en texto plano.
          </li>
          <li>
            <strong>Uso del servicio:</strong> turnos reservados y cancelados, núcleo y pisos de
            origen y destino, check-ins (hora y ascensor donde escaneaste el QR), faltas, respuestas
            opcionales a la encuesta de espera y tus preferencias de notificaciones.
          </li>
          <li>
            <strong>Acceso prioritario:</strong> si lo pedís, el certificado médico o de
            discapacidad que subís, la categoría (movilidad reducida o docente), el estado de la
            solicitud, su vencimiento y cuándo diste tu consentimiento.
          </li>
          <li>
            <strong>Datos técnicos:</strong> los proveedores de hosting registran la dirección IP,
            el navegador y la hora de cada pedido en sus registros de seguridad.
          </li>
        </ul>
        <p>
          No usamos tus datos para publicidad, no los vendemos y no armamos perfiles comerciales.
        </p>
      </section>

      <section aria-labelledby="privacy-health">
        <h2 id="privacy-health">3. Datos de salud</h2>
        <p>
          El certificado de prioridad es un <strong>dato sensible</strong> (arts. 2 y 7 de la Ley
          25.326). Solo lo tratamos con tu <strong>consentimiento expreso</strong>, que das al
          marcar la casilla cuando lo subís, y únicamente para validar el acceso prioritario. Nadie
          está obligado a darlo: sin certificado podés usar SmartElevate como usuario común.
        </p>
        <ul>
          <li>Se guarda en un almacenamiento privado, cifrado y sin acceso público.</li>
          <li>
            Solo lo ve el equipo revisor, mediante enlaces temporales que vencen en pocos minutos.
          </li>
          <li>
            Se <strong>borra apenas se resuelve</strong> la solicitud. Después solo conservamos si
            fue aprobada o rechazada y hasta cuándo vale.
          </li>
          <li>
            No registramos su contenido ni el nombre del archivo en los registros del sistema.
          </li>
        </ul>
      </section>

      <section aria-labelledby="privacy-purpose">
        <h2 id="privacy-purpose">4. Para qué los usamos</h2>
        <ul>
          <li>Crear y proteger tu cuenta, verificar tu email y recuperar tu contraseña.</li>
          <li>
            Reservar, asignar y cancelar turnos, registrar el check-in y aplicar las reglas de cupo,
            faltas y prioridad.
          </li>
          <li>Mandarte los avisos que elegiste recibir.</li>
          <li>
            Medir la congestión de los ascensores con <strong>indicadores agregados</strong> que no
            identifican a nadie, como parte del trabajo académico.
          </li>
        </ul>
        <p>
          La base legal es tu consentimiento al registrarte y, para los datos de salud, tu
          consentimiento expreso. Solo pedimos los datos necesarios para estas finalidades (art. 4
          de la Ley 25.326).
        </p>
      </section>

      <section aria-labelledby="privacy-providers">
        <h2 id="privacy-providers">5. Con quién los compartimos</h2>
        <p>
          No cedemos tus datos a terceros. Para funcionar usamos proveedores que los procesan solo
          por nuestra cuenta y bajo sus propias medidas de seguridad:
        </p>
        <ul>
          <li>
            <strong>Vercel</strong> (sitio web) y <strong>Render</strong> (servidor), en Estados
            Unidos.
          </li>
          <li>
            <strong>Neon</strong> (base de datos), en Estados Unidos (región AWS Ohio).
          </li>
          <li>
            <strong>Brevo</strong> (envío de emails), con servidores en la Unión Europea.
          </li>
          <li>
            <strong>Cloudflare R2</strong> (almacenamiento privado de certificados), en la región
            que asigna Cloudflare.
          </li>
        </ul>
        <p>
          Esto implica una <strong>transferencia internacional</strong> de datos (art. 12 de la Ley
          25.326). Algunos de estos países no figuran entre los de protección adecuada de la
          Disposición 60/2016 de la AAIP, por eso la hacemos con tu consentimiento y con proveedores
          que ofrecen garantías contractuales de protección de datos. Solo entregaríamos datos a una
          autoridad si una ley o una orden judicial lo exige.
        </p>
      </section>

      <section aria-labelledby="privacy-retention">
        <h2 id="privacy-retention">6. Cuánto tiempo los guardamos</h2>
        <ul>
          <li>Los datos de la cuenta y del uso, mientras tengas la cuenta.</li>
          <li>El certificado de prioridad, hasta que se resuelve la solicitud.</li>
          <li>
            Si pedís la baja, borramos tus datos personales. Solo quedan los indicadores agregados,
            que no te identifican.
          </li>
          <li>Al terminar el proyecto académico, eliminamos la base de datos o la anonimizamos.</li>
        </ul>
      </section>

      <section aria-labelledby="privacy-security">
        <h2 id="privacy-security">7. Cómo los protegemos</h2>
        <p>
          Aplicamos las medidas del art. 9 de la Ley 25.326 y de la Resolución 47/2018 de la AAIP:
          conexiones cifradas (HTTPS/TLS), contraseñas con hash, acceso por roles, almacenamiento
          privado para los certificados y acceso restringido a los paneles de los proveedores.
          Quienes acceden a los datos tienen deber de confidencialidad (art. 10). Si detectamos un
          incidente que afecte tus datos, te vamos a avisar.
        </p>
      </section>

      <section aria-labelledby="privacy-storage">
        <h2 id="privacy-storage">8. Cookies y almacenamiento en tu navegador</h2>
        <p>
          No usamos cookies de publicidad ni de seguimiento, ni herramientas de analítica de
          terceros. Solo usamos lo técnico e indispensable:
        </p>
        <ul>
          <li>Una cookie de sesión segura para mantenerte con la sesión iniciada.</li>
          <li>
            El almacenamiento local del navegador para recordar tu preferencia de vista (Pantalla o
            Teléfono).
          </li>
        </ul>
      </section>

      <section aria-labelledby="privacy-rights">
        <h2 id="privacy-rights">9. Tus derechos</h2>
        <p>
          Podés pedir <strong>acceso</strong> a tus datos, su <strong>rectificación</strong>,{' '}
          <strong>actualización</strong> o <strong>supresión</strong>, y retirar tu consentimiento
          cuando quieras (arts. 14 a 16 de la Ley 25.326). Escribinos a {mail} desde tu email
          institucional. Respondemos los pedidos de acceso en 10 días corridos y los de
          rectificación o supresión en 5 días hábiles.
        </p>
        <p>
          Si estás en la Unión Europea, además tenés los derechos de limitación, portabilidad y
          oposición del RGPD, con respuesta dentro de un mes, y podés reclamar ante tu autoridad de
          protección de datos.
        </p>
        <p className="legal__notice">
          El titular de los datos personales tiene la facultad de ejercer el derecho de acceso a los
          mismos en forma gratuita a intervalos no inferiores a seis meses, salvo que se acredite un
          interés legítimo al efecto conforme lo establecido en el artículo 14, inciso 3 de la Ley
          N° 25.326. La AGENCIA DE ACCESO A LA INFORMACIÓN PÚBLICA, en su carácter de Órgano de
          Control de la Ley N° 25.326, tiene la atribución de atender las denuncias y reclamos que
          interpongan quienes resulten afectados en sus derechos por incumplimiento de las normas
          vigentes en materia de protección de datos personales.{' '}
          <a href="https://www.argentina.gob.ar/aaip" target="_blank" rel="noreferrer">
            Sitio de la AAIP
          </a>
          .
        </p>
      </section>

      <section aria-labelledby="privacy-changes">
        <h2 id="privacy-changes">10. Cambios en esta política</h2>
        <p>
          Si cambiamos algo importante, te lo avisamos en la app antes de que se aplique. La fecha
          de arriba indica la última versión. Ver también los{' '}
          <Link to="/terminos">Términos de uso</Link>.
        </p>
      </section>

      <LegalFooter />
    </article>
  );
}
