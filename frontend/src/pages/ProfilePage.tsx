import { Link, useNavigate } from 'react-router';
import { Avatar } from '../components/Avatar';
import { Chip } from '../components/Chip';
import { PriorityAccessCard } from '../features/priority/PriorityAccessCard';
import '../features/priority/priority.css';
import { useSession } from '../hooks/useSession';
import type { DeclaredUserType } from '../types/pending';

const userTypeLabels: Record<DeclaredUserType, string> = {
  STUDENT: 'Estudiante',
  TEACHER: 'Docente',
  STAFF: 'Personal',
};

export function ProfilePage() {
  const { user, logout } = useSession();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <>
      <h1 className="visually-hidden">Mi perfil</h1>
      {user && (
        <header className="profile-header">
          <Avatar name={user.fullName} />
          <div className="profile-header__info">
            <h2>{user.fullName}</h2>
            <Chip tone="neutral">{userTypeLabels[user.declaredUserType]}</Chip>
            <p>Legajo {user.legajo}</p>
            <p>{user.email}</p>
          </div>
        </header>
      )}
      <PriorityAccessCard />
      <nav aria-label="Opciones del perfil">
        <ul className="profile-options">
          <li>
            <Link to="/perfil/viajes">Mis viajes</Link>
          </li>
          <li>
            <Link to="/perfil/notificaciones">Notificaciones</Link>
          </li>
          <li>
            <button type="button" onClick={handleLogout}>
              Cerrar sesión
            </button>
          </li>
        </ul>
      </nav>
    </>
  );
}
