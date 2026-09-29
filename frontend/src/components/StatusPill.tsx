import './StatusPill.css';

export type CongestionLevel = 'LOW' | 'MEDIUM' | 'HIGH';

const labels: Record<CongestionLevel, string> = {
  LOW: 'Baja',
  MEDIUM: 'Media',
  HIGH: 'Alta',
};

type StatusPillProps = {
  level: CongestionLevel;
  className?: string;
};

/**
 * Nivel de congestión (Figma: StatusPill). El color del punto es un refuerzo:
 * la etiqueta Baja/Media/Alta siempre se muestra.
 */
export function StatusPill({ level, className }: StatusPillProps) {
  const modifier = level.toLowerCase();
  return (
    <span
      className={['status-pill', `status-pill--${modifier}`, className].filter(Boolean).join(' ')}
    >
      <span className="status-pill__dot" aria-hidden="true" />
      {labels[level]}
    </span>
  );
}
