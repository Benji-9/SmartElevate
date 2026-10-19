import { Link, useNavigate } from 'react-router';
import { Avatar } from '../components/Avatar';
import { Button } from '../components/Button';
import { Chip, type ChipTone } from '../components/Chip';
import logo from '../assets/smartelevate-logo-horizontal.svg';
import { LegalFooter } from '../components/LegalFooter';
import { formatShortDate } from '../features/turn/format';
import { PriorityInfoCard } from '../features/priority/PriorityInfoCard';
import '../features/priority/priority.css';
import { useSession } from '../hooks/useSession';
import type { DeclaredUserType, User } from '../types/api';

const userTypes: Record<DeclaredUserType, { label: string; tone: ChipTone }> = {
  STUDENT: { label: 'Estudiante', tone: 'neutral' },
  TEACHER: { label: 'Docente', tone: 'neutral' },
};

const arrow = (
  <span className="profile-options__arrow" aria-hidden="true">
    →
  </span>
);

/**
 * Estado de la prioridad de docente: con `priorityUntil` (UTC, nulo si no vence) dice hasta
 * cuándo vale, en hora de Buenos Aires.
 */
function priorityStatus(user: User) {
  if (user.priority === 'TEACHER') {
    return user.priorityUntil
      ? `Prioridad de docente: vigente hasta el ${formatShortDate(user.priorityUntil)}`
      : 'Prioridad de docente: vigente';
  }
  if (user.declaredUserType === 'TEACHER' && user.priority === 'NONE')
    return 'Prioridad de docente: pendiente de validación';
}

export function ProfilePage() {
  const { user, logout } = useSession();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="profile">
      {/* El TopBar ya lleva el logo en Pantalla. */}
      <img
        className="profile-logo only-mobile"
        src={logo}
        alt="SmartElevate"
        width={131}
        height={32}
      />
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
            {priorityStatus(user) && <p>{priorityStatus(user)}</p>}
          </div>
        </header>
      )}
      <PriorityInfoCard />
      <div className="profile-nav">
        <nav aria-label="Opciones del perfil">
          <ul className="profile-options">
            <li>
              <Link to="/perfil/viajes">Mis viajes{arrow}</Link>
            </li>
            <li>
              <Link to="/perfil/notificaciones">Notificaciones{arrow}</Link>
            </li>
            {user?.role === 'ADMIN' && (
              <li>
                <Link to="/admin">Panel de administración{arrow}</Link>
              </li>
            )}
          </ul>
        </nav>
        {/* Es una acción, no un destino: fuera de la lista y sin flecha (#159). */}
        <Button variant="secondary" onClick={handleLogout}>
          Cerrar sesión
        </Button>
      </div>
      <LegalFooter withUadeLogo />
    </div>
  );
}
