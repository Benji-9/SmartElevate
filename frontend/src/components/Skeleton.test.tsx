import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Skeleton } from './Skeleton';

describe('Skeleton', () => {
  it('anuncia la carga con su texto y oculta los bloques al lector de pantalla', () => {
    render(<Skeleton label="Cargando tus viajes…" rows={3} />);

    const status = screen.getByRole('status');
    expect(status).toHaveTextContent('Cargando tus viajes…');
    expect(status.querySelectorAll('[aria-hidden="true"]')).toHaveLength(3);
  });
});
