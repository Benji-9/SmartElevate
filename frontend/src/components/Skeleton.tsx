import './Skeleton.css';

type SkeletonProps = {
  /** Lo que anuncia el lector de pantalla mientras carga ("Cargando tu turno…"). */
  label: string;
  /** Bloques apilados con la forma aproximada del contenido que va a llegar. */
  rows?: number;
  /** Alto de cada bloque, en px. */
  height?: number;
  className?: string;
};

/** Marcador de carga con la forma del contenido: evita saltos de layout (#159). */
export function Skeleton({ label, rows = 1, height = 20, className }: SkeletonProps) {
  return (
    <div role="status" className={['skeleton', className].filter(Boolean).join(' ')}>
      <span className="visually-hidden">{label}</span>
      {Array.from({ length: rows }, (_, index) => (
        <span key={index} className="skeleton__block" style={{ height }} aria-hidden="true" />
      ))}
    </div>
  );
}
