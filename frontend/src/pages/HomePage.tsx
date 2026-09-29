import { useLocation } from 'react-router';
import { CongestionNow } from '../features/congestion/CongestionNow';
import { ActiveTurnCard } from '../features/turn/ActiveTurnCard';
import { formatDate } from '../features/turn/format';
import { useSession } from '../hooks/useSession';
import './HomePage.css';

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join('');

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
      <header className="home__header">
        <div>
          <h1>Hola, {fullName.split(/\s+/)[0]}</h1>
          <p className="home__date">{formatDate(new Date())}</p>
        </div>
        <span className="home__avatar" aria-hidden="true">
          {initials(fullName)}
        </span>
      </header>
      <ActiveTurnCard />
      <CongestionNow />
    </>
  );
}
