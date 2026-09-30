import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Chip, type ChipTone } from './Chip';

describe('Chip', () => {
  it.each<[ChipTone, string]>([
    ['accent', 'Confirmado'],
    ['neutral', 'Cancelado'],
    ['danger', 'Falta'],
    ['baja', 'Baja'],
    ['media', 'Media'],
    ['alta', 'Alta'],
    ['alumnos', 'Estudiante'],
    ['docentes', 'Docente'],
  ])('con el tono %s muestra el texto, no solo el color', (tone, text) => {
    render(<Chip tone={tone}>{text}</Chip>);
    expect(screen.getByText(text)).toBeInTheDocument();
  });

  it('el punto de color es decorativo: no agrega nada al texto accesible', () => {
    render(<Chip tone="alta">Alta</Chip>);
    expect(screen.getByText('Alta').textContent).toBe('Alta');
  });
});
