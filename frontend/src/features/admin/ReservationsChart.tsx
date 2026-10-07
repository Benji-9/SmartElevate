import { useId, type CSSProperties } from 'react';
import type { AdminKpis } from '../../types/pending';
import { formatNumber } from './format';

type ReservationsChartProps = { data: AdminKpis['reservationsByHour'] };

/** Desde qué parte del máximo una franja cuenta como de las más cargadas (barra sólida). */
const PEAK_RATIO = 0.8;

/**
 * Barras de reservas por hora, en CSS. El dibujo es decorativo (`aria-hidden`): los
 * lectores de pantalla leen la tabla equivalente.
 */
export function ReservationsChart({ data }: ReservationsChartProps) {
  const titleId = useId();
  const max = Math.max(1, ...data.map((d) => d.reservations));

  return (
    <figure className="admin-card hour-chart" aria-labelledby={titleId}>
      <figcaption>
        <h2 id={titleId} className="text-card-title">
          Reservas por franja
        </h2>
      </figcaption>

      <div className="hour-chart__plot" aria-hidden="true">
        <span className="hour-chart__y-title">Reservas</span>
        <span className="hour-chart__y-ticks">
          <span>{formatNumber(max)}</span>
          <span>{formatNumber(Math.round(max / 2))}</span>
          <span>0</span>
        </span>
        <ol className="hour-chart__bars">
          {data.map(({ hour, reservations }) => (
            <li key={hour} style={{ '--value': reservations / max } as CSSProperties}>
              <span className="hour-chart__value">{formatNumber(reservations)}</span>
              <span
                className={`hour-chart__bar${reservations >= max * PEAK_RATIO ? ' hour-chart__bar--peak' : ''}`}
              />
              <span className="hour-chart__hour">{hour}</span>
            </li>
          ))}
        </ol>
        <span className="hour-chart__x-title">Hora del día (h)</span>
      </div>

      <table className="visually-hidden">
        <caption>Reservas por hora del día</caption>
        <thead>
          <tr>
            <th scope="col">Hora</th>
            <th scope="col">Reservas</th>
          </tr>
        </thead>
        <tbody>
          {data.map(({ hour, reservations }) => (
            <tr key={hour}>
              <th scope="row">
                {hour}:00 a {hour + 1}:00
              </th>
              <td>{formatNumber(reservations)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}
