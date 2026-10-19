import { useLocation } from 'react-router';
import { Avatar } from '../components/Avatar';
import logo from '../assets/smartelevate-logo-horizontal.svg';
import { CongestionNow } from '../features/congestion/CongestionNow';
import { ActiveTurnCard } from '../features/turn/ActiveTurnCard';
import { formatDay } from '../features/turn/format';
import { useSession } from '../hooks/useSession';
import './HomePage.css';

export function HomePage() {
  const { user } = useSession();
  const location = useLocation();
  const notice = (location.state as { notice?: string } | null)?.notice;
  const fullName = user?.fullName ?? '';

  return (
    <>
      {notice && (
        <p role="status" className="home__notice">
          {notice}
        </p>
      )}
      {/* En Pantalla, dos columnas: saludo y turno a la izquierda, congestión a la derecha. */}
      <div className="home">
        <div className="home__main">
          {/* El TopBar ya lleva el logo en Pantalla. */}
          <img
            className="home__logo only-mobile"
            src={logo}
            alt="SmartElevate"
            width={131}
            height={32}
          />
          <header className="home__header">
            <div>
              <h1 className="home__title text-title">Hola, {fullName.split(/\s+/)[0]}</h1>
              <p className="home__date text-body-sm">{formatDay(new Date())}</p>
            </div>
            <Avatar name={fullName} className="home__avatar" />
          </header>
          <ActiveTurnCard />
        </div>
        <CongestionNow />
      </div>
    </>
  );
}
