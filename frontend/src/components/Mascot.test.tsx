import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Mascot } from './Mascot';

describe('Mascot', () => {
  it('es decorativa: sin texto alternativo y con el alto pedido', () => {
    const { container } = render(<Mascot size={100} />);
    const img = container.querySelector('img');
    expect(img).toHaveAttribute('alt', '');
    expect(img).toHaveAttribute('height', '100');
  });

  it('calcula el ancho con la proporción de la pose', () => {
    const { container } = render(<Mascot pose="senala" size={230} />);
    expect(container.querySelector('img')).toHaveAttribute('width', '200');
  });
});
