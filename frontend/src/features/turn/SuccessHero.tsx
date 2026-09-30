import type { ReactNode } from 'react';

/** Hero de confirmación (turno confirmado, viaje registrado): tilde, título `h1` y detalle. */
export function SuccessHero({ title, children }: { title: string; children: ReactNode }) {
  return (
    <header className="success-hero">
      <span className="success-hero__icon" aria-hidden="true">
        <svg viewBox="0 0 44 44" width="44" height="44">
          <path
            d="M11 23.5l7.5 7.5L33 15"
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
