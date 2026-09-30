import { Link, useNavigate } from 'react-router';
import { Avatar } from '../components/Avatar';
import { Chip, type ChipTone } from '../components/Chip';
import { PriorityAccessCard } from '../features/priority/PriorityAccessCard';
import '../features/priority/priority.css';
import { useSession } from '../hooks/useSession';
import type { DeclaredUserType } from '../types/pending';

const userTypes: Record<DeclaredUserType, { label: string; tone: ChipTone }> = {
  STUDENT: { label: 'Estudiante', tone: 'alumnos' },
  TEACHER: { label: 'Docente', tone: 'docentes' },
  STAFF: { label: 'Personal', tone: 'neutral' },
};

const arrow = (
  <span className="profile-options__arrow" aria-hidden="true">
    →
  </span>
);

export function ProfilePage() {
  const { user, logout } = useSession();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="profile">
      <h1 className="profile-title text-heading">Mi perfil</h1>
      {user && (
        <header className="profile-header">
          <Avatar name={user.fullName} />
          <div className="profile-header__info">
            <h2 className="text-heading-sm">{user.fullName}</h2>
            <Chip tone={userTypes[user.declaredUserType].tone}>
              {userTypes[user.declaredUserType].label}
            </Chip>
            <p>Legajo {user.legajo}</p>
            <p>{user.email}</p>
          </div>
        </header>
      )}
      <PriorityAccessCard />
      <nav className="profile-nav" aria-label="Opciones del perfil">
        <ul className="profile-options">
          <li>
            <Link to="/perfil/viajes">Mis viajes{arrow}</Link>
          </li>
          <li>
            <Link to="/perfil/notificaciones">Notificaciones{arrow}</Link>
          </li>
          <li>
            <button type="button" onClick={handleLogout}>
              Cerrar sesión{arrow}
            </button>
          </li>
        </ul>
      </nav>
    </div>
  );
}
