import type { ReactNode } from 'react';

/**
 * Hero de cierre (turno confirmado, viaje registrado): ícono, título `h1` y detalle.
 * `tone="danger"` cambia la tilde por un signo de advertencia (p. ej. check-in que cuenta como falta).
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
      <span className="success-hero__icon" aria-hidden="true">
        <svg viewBox="0 0 44 44" width="44" height="44">
          <path
            d={tone === 'danger' ? 'M22 11v14M22 33v0.5' : 'M11 23.5l7.5 7.5L33 15'}
            fill="none"
            stroke="currentColor"
            strokeWidth="5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
      <h1 className="text-title">{title}</h1>
      <p className="success-hero__detail text-body">{children}</p>
    </header>
  );
}
