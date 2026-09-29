import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Chip } from './Chip';
import { StatusPill, type CongestionLevel } from './StatusPill';

describe('StatusPill', () => {
  it.each<[CongestionLevel, string]>([
    ['LOW', 'Baja'],
    ['MEDIUM', 'Media'],
    ['HIGH', 'Alta'],
  ])('muestra el nivel %s con su etiqueta, no solo con color', (level, label) => {
    render(<StatusPill level={level} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});

describe('Chip', () => {
  it('muestra su contenido', () => {
    render(<Chip>Confirmado</Chip>);
    expect(screen.getByText('Confirmado')).toBeInTheDocument();
  });
});
