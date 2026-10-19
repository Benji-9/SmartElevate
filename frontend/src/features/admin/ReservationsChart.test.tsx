import { render, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ReservationsChart } from './ReservationsChart';

const ticks = (max: number) => {
  const { container } = render(<ReservationsChart data={[{ hour: 8, reservations: max }]} />);
  const el = container.querySelector('.hour-chart__y-ticks') as HTMLElement;
  return within(el)
    .getAllByText(/\d/)
    .map((n) => n.textContent);
};

describe('ReservationsChart', () => {
  it('no repite marcas del eje con máximo 1', () => {
    expect(ticks(1)).toEqual(['1', '0']);
  });

  it('con máximo impar no inventa una marca intermedia', () => {
    expect(ticks(3)).toEqual(['3', '0']);
  });

  it('con máximo par marca la mitad', () => {
    expect(ticks(4)).toEqual(['4', '2', '0']);
  });
});
