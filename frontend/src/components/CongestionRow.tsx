import { congestionLabel, congestionTone } from '../features/congestion/level';
import type { CongestionLevel } from '../types/pending';
import './CongestionRow.css';

type CongestionRowProps = {
  label: string;
  level: CongestionLevel;
  /** Ocupación de 0 a 1: largo de la barra. */
  value: number;
  /** Texto secundario debajo del nombre (p. ej. la espera estimada). */
  detail?: string;
  className?: string;
};

/**
 * Nombre del núcleo · barra · nivel escrito (Figma: CongestionRow). La barra es decorativa:
 * el nivel siempre va en texto.
 */
export function CongestionRow({ label, level, value, detail, className }: CongestionRowProps) {
  const width = `${Math.round(Math.min(1, Math.max(0, value)) * 100)}%`;
  return (
    <div
      className={['congestion-row', `congestion-row--${congestionTone[level]}`, className]
        .filter(Boolean)
        .join(' ')}
    >
      <span className="congestion-row__text">
        <span className="congestion-row__label">{label}</span>
        {detail && <span className="congestion-row__detail text-caption">{detail}</span>}
      </span>
      <span className="congestion-row__track" aria-hidden="true">
        <span className="congestion-row__fill" style={{ width }} />
      </span>
      <span className="congestion-row__level">{congestionLabel[level]}</span>
    </div>
  );
}
