import type { ReactNode } from 'react';
import './Chip.css';

type ChipProps = {
  children: ReactNode;
  /** `primary` para estados normales ("Confirmado"), `danger` para faltas o rechazos. */
  tone?: 'primary' | 'neutral' | 'danger';
  className?: string;
};

/** Etiqueta corta de estado o dato (Figma: Chip). No es interactiva. */
export function Chip({ children, tone = 'primary', className }: ChipProps) {
  return (
    <span className={['chip', `chip--${tone}`, className].filter(Boolean).join(' ')}>
      {children}
    </span>
  );
}
