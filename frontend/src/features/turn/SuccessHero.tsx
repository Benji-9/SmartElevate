import type { ReactNode } from 'react';
import { Mascot } from '../../components/Mascot';

/**
 * Hero de cierre (turno confirmado, viaje registrado): ícono, título `h1` y detalle.
 * En éxito lo ilustra Elevy; `tone="danger"` cambia a un signo de advertencia (p. ej. check-in que cuenta como falta).
 */
export function SuccessHero({
  title,
  tone = 'success',
  children,
}: {
  title: string;
  tone?: 'success' | 'danger';
  children: ReactNode;
}) {
  return (
    <header className={`success-hero success-hero--${tone}`}>
      {tone === 'success' ? (
        <Mascot size={120} />
      ) : (
        <span className="success-hero__icon" aria-hidden="true">
          <svg viewBox="0 0 44 44" width="44" height="44">
            <path
              d="M22 11v14M22 33v0.5"
              fill="none"
              stroke="currentColor"
              strokeWidth="5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
      )}
      <h1 className="text-title">{title}</h1>
      <p className="success-hero__detail text-body">{children}</p>
    </header>
  );
}
