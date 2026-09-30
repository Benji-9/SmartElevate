import type { ReactNode } from 'react';
import './Chip.css';

export type ChipTone =
  'accent' | 'neutral' | 'danger' | 'baja' | 'media' | 'alta' | 'alumnos' | 'docentes';

type ChipProps = {
  children: ReactNode;
  /**
   * `accent` para estados de reserva ("Confirmado"), `neutral` y `danger` para el historial
   * y los rechazos. Congestión (`baja`/`media`/`alta`) y tipo de usuario (`alumnos`/`docentes`)
   * llevan un punto de color: es un refuerzo, el texto siempre va.
   */
  tone?: ChipTone;
  className?: string;
};

const dotted = new Set<ChipTone>(['baja', 'media', 'alta', 'alumnos', 'docentes']);

/** Etiqueta corta de estado o dato (Figma: Chip). No es interactiva. */
export function Chip({ children, tone = 'accent', className }: ChipProps) {
  return (
    <span
      className={['chip', 'text-caption-strong', `chip--${tone}`, className]
        .filter(Boolean)
        .join(' ')}
    >
      {dotted.has(tone) && <span className="chip__dot" aria-hidden="true" />}
      {children}
    </span>
  );
}
