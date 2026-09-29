import { Link, useNavigate } from 'react-router';
import { Button } from '../components/Button';
import { useSession } from '../hooks/useSession';

export function ProfilePage() {
  const { user, logout } = useSession();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <section>
      <h1>Mi perfil</h1>
      <p>{user?.fullName}</p>
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
      <Button variant="secondary" onClick={handleLogout}>
        Cerrar sesión
      </Button>
    </section>
  );
}
