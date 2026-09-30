import { useEffect, useState } from 'react';
import { Button } from '../../components/Button';
import { CongestionRow } from '../../components/CongestionRow';
import { ApiError, getCongestion } from '../../services/api';
import type { CongestionLevel, CongestionSnapshot } from '../../types/pending';
import './CongestionNow.css';

// ponytail: `CoreCongestion` no trae ocupación; el largo de la barra sale del nivel.
// Cuando la API exponga la ocupación del núcleo (0–1), pasarla directo a `value`.
const levelValue: Record<CongestionLevel, number> = { LOW: 1 / 3, MEDIUM: 2 / 3, HIGH: 1 };

const minutesAgo = new Intl.RelativeTimeFormat('es-AR', { style: 'short', numeric: 'always' });

const errorMessage = (error: unknown) =>
  error instanceof ApiError ? error.message : 'No pudimos cargar la congestión.';

/** Congestión por núcleo. Se vuelve a pedir cuando lo indica el servidor (`refreshAfterSeconds`). */
function useCongestion() {
  const [snapshot, setSnapshot] = useState<CongestionSnapshot | null>(null);
  // Se calcula al recibir los datos (no en el render): se actualiza con cada refresco.
  const [minutesOld, setMinutesOld] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    getCongestion().then(
      (data) => {
        if (!active) return;
        setSnapshot(data);
        setMinutesOld(Math.max(0, Math.floor((Date.now() - Date.parse(data.updatedAt)) / 60_000)));
        setError(null);
        timer = setTimeout(() => setAttempt((n) => n + 1), data.refreshAfterSeconds * 1000);
      },
      (err: unknown) => {
        if (active) setError(errorMessage(err));
      },
    );
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [attempt]);

  const retry = () => {
    setError(null);
    setAttempt((n) => n + 1);
  };
  return { snapshot, minutesOld, error, retry };
}

export function CongestionNow() {
  const { snapshot, minutesOld, error, retry } = useCongestion();

  return (
    <section className="congestion" aria-labelledby="congestion-title">
      <div className="congestion__header">
        <h2 id="congestion-title" className="text-card-title">
          Congestión ahora
        </h2>
        {snapshot && (
          <p className="congestion__updated text-caption">
            Actualizado {minutesAgo.format(-minutesOld, 'minute')}
          </p>
        )}
      </div>

      {error ? (
        <div role="alert" className="congestion__error">
          <p>{error}</p>
          <Button variant="secondary" block={false} onClick={retry}>
            Reintentar
          </Button>
        </div>
      ) : !snapshot ? (
        <p role="status" className="page-placeholder">
          Cargando congestión…
        </p>
      ) : snapshot.cores.length === 0 ? (
        <p className="page-placeholder">No hay núcleos para mostrar.</p>
      ) : (
        <ul className="congestion__list">
          {snapshot.cores.map((core) => (
            <li key={core.coreId}>
              <CongestionRow
                label={core.name}
                level={core.level}
                value={levelValue[core.level]}
                detail={`~${core.estimatedWaitMinutes} min de espera`}
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
