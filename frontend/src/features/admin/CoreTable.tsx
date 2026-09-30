import { useId } from 'react';
import { Chip } from '../../components/Chip';
import { congestionLabel, congestionTone } from '../congestion/level';
import type { CoreKpis } from '../../types/pending';
import { formatMinutes, formatNumber, formatPercent } from './format';

/** Tabla "Estado por núcleo". */
export function CoreTable({ cores }: { cores: CoreKpis[] }) {
  const titleId = useId();

  return (
    <section className="admin-card" aria-labelledby={titleId}>
      <h2 id={titleId}>Estado por núcleo</h2>
      {cores.length === 0 ? (
        <p className="page-placeholder">No hay núcleos para mostrar.</p>
      ) : (
        <div className="core-table__scroll">
          <table className="core-table" aria-labelledby={titleId}>
            <thead>
              <tr>
                <th scope="col">Núcleo</th>
                <th scope="col">Reservas</th>
                <th scope="col">Ocupación</th>
                <th scope="col">Espera promedio</th>
                <th scope="col">Estado</th>
              </tr>
            </thead>
            <tbody>
              {cores.map((core) => (
                <tr key={core.coreId}>
                  <th scope="row">{core.name}</th>
                  <td>{formatNumber(core.reservations)}</td>
                  <td>{formatPercent(core.occupancyPercent)}</td>
                  <td>{formatMinutes(core.avgWaitSeconds)}</td>
                  <td>
                    <Chip tone={congestionTone[core.congestion]}>
                      {congestionLabel[core.congestion]}
                    </Chip>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
