import { useLocation } from 'react-router';
import { Avatar } from '../components/Avatar';
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
          <header className="home__header">
            <div>
              <h1 className="home__title text-title">Hola, {fullName.split(/\s+/)[0]}</h1>
              <p className="home__date text-body-sm">{formatDay(new Date())}</p>
            </div>
            <Avatar name={fullName} />
          </header>
          <ActiveTurnCard />
        </div>
        <CongestionNow />
      </div>
    </>
  );
}
