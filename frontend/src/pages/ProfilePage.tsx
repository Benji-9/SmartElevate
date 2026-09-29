import { Link } from 'react-router';

export function ProfilePage() {
  return (
    <section>
      <h1>Mi perfil</h1>
      <p className="page-placeholder">
        Próximamente: tus datos y la solicitud de acceso prioritario.
      </p>
      <ul>
        <li>
          <Link to="/perfil/viajes">Mis viajes</Link>
        </li>
        <li>
          <Link to="/perfil/notificaciones">Notificaciones</Link>
        </li>
      </ul>
    </section>
  );
}
