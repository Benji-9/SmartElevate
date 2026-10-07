import type { NoShowStatus } from '../../types/pending';
import { formatDate, formatTime } from '../turn/format';

const faltas = (n: number) => `${n} ${n === 1 ? 'falta' : 'faltas'}`;

/**
 * Aviso de faltas (turnos.md §5). Los parámetros de la regla vienen de la API.
 * A los prioritarios (`exempt`) no se los suspende: no se les muestra esa amenaza.
 */
export function NoShowNotice({ status }: { status: NoShowStatus }) {
  const { recentNoShows, threshold, windowDays, suspensionHours, suspendedUntil, exempt } = status;
  const suspended = suspendedUntil !== null && !exempt;
  if (!suspended && recentNoShows === 0) return null;

  const summary = `Tenés ${faltas(recentNoShows)} en los últimos ${windowDays} días.`;
  return (
    <section className="trips__notice" aria-labelledby="no-show-title">
      <h2 id="no-show-title">{suspended ? 'No podés reservar por ahora' : 'Tenés faltas'}</h2>
      {suspended ? (
        <p>
          {summary} Vas a poder volver a reservar el {formatDate(new Date(suspendedUntil))} a las{' '}
          {formatTime(suspendedUntil)}.
        </p>
      ) : exempt ? (
        <p>{summary} Si no vas a usar un turno, cancelalo para liberar el lugar.</p>
      ) : (
        <p>
          {summary} Con {faltas(threshold)} en {windowDays} días no vas a poder reservar por{' '}
          {suspensionHours} h. Si no vas a usar un turno, cancelalo a tiempo.
        </p>
      )}
    </section>
  );
}
