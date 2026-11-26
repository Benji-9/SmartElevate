import { render, screen, within } from '@testing-library/react';
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
  it('la tabla equivalente marca las horas pico, igual que las barras', () => {
    render(
      <ReservationsChart
        data={[
          { hour: 8, reservations: 100 },
          { hour: 9, reservations: 80 },
          { hour: 10, reservations: 79 },
        ]}
      />,
    );
    const table = screen.getByRole('table', { name: 'Reservas por hora del día' });
    const row = (name: RegExp) => within(table).getByRole('row', { name });

    expect(row(/8:00 a 9:00/)).toHaveTextContent('100 (hora pico)');
    expect(row(/9:00 a 10:00/)).toHaveTextContent('80 (hora pico)');
    expect(row(/10:00 a 11:00/)).not.toHaveTextContent('pico');
  });

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
