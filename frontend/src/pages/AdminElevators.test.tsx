import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedIn, stubApi, testUser } from '../test/stubApi';
import type { AdminElevator } from '../types/api';

const elevator = (code: string, active: boolean, upcomingReservations: number): AdminElevator => ({
  code,
  coreName: 'Independencia 2',
  building: 'INDEPENDENCIA',
  usage: 'COMMON',
  active,
  upcomingReservations,
});

/** Servidor simulado con estado: marcar y reactivar cambian la lista. */
function renderPage(initial: AdminElevator[], { outStatus = 200 } = {}) {
  let elevators = initial;
  const fetchMock = stubApi({
    ...signedIn({ ...testUser, role: 'ADMIN' }),
    'GET /admin/elevators': () => ({ body: elevators }),
    'POST /admin/elevators/33/out-of-service': () => {
      if (outStatus !== 200)
        return { status: outStatus, body: { message: 'Ascensor inexistente' } };
      const cancelled = elevators.find((e) => e.code === '33')!.upcomingReservations;
      elevators = elevators.map((e) =>
        e.code === '33' ? { ...e, active: false, upcomingReservations: 0 } : e,
      );
      return { body: { code: '33', active: false, cancelledReservations: cancelled } };
    },
    'POST /admin/elevators/34/in-service': () => {
      elevators = elevators.map((e) => (e.code === '34' ? { ...e, active: true } : e));
      return { body: { code: '34', active: true, cancelledReservations: 0 } };
    },
  });
  render(
    <MemoryRouter initialEntries={['/admin/ascensores']}>
      <App />
    </MemoryRouter>,
  );
  return fetchMock;
}

describe('AdminElevatorsPage', () => {
  it('muestra el estado y confirma cuántas reservas se cancelan antes de marcar', async () => {
    const fetchMock = renderPage([elevator('33', true, 4), elevator('34', false, 0)]);
    const user = userEvent.setup();

    const row33 = (await screen.findByRole('rowheader', { name: '33' })).closest('tr')!;
    expect(within(row33).getByText('En servicio')).toBeInTheDocument();
    const row34 = screen.getByRole('rowheader', { name: '34' }).closest('tr')!;
    expect(within(row34).getByText('Fuera de servicio')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', { name: 'Marcar fuera de servicio el ascensor 33' }),
    );
    expect(
      screen.getByText(/Se cancelan 4 reservas sin falta y se avisa a cada persona/),
    ).toBeInTheDocument();
    // Cancelar no manda nada.
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('out-of-service'))).toBe(
      false,
    );

    await user.click(
      screen.getByRole('button', { name: 'Marcar fuera de servicio el ascensor 33' }),
    );
    await user.click(screen.getByRole('button', { name: 'Sí, marcar' }));

    expect(
      await screen.findByText('Ascensor 33 fuera de servicio. Se cancelaron 4 reservas.'),
    ).toBeInTheDocument();
    const updated = (await screen.findAllByText('Fuera de servicio')).length;
    expect(updated).toBe(2);
  });

  it('deshabilita las acciones mientras el pedido está en curso', async () => {
    const fetchMock = renderPage([elevator('33', true, 2), elevator('34', true, 0)]);
    const real = fetchMock.getMockImplementation()!;
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      url.includes('out-of-service') ? new Promise<Response>(() => {}) : real(url, init),
    );
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole('button', { name: 'Marcar fuera de servicio el ascensor 33' }),
    );
    await user.click(screen.getByRole('button', { name: 'Sí, marcar' }));

    expect(
      screen.getByRole('button', { name: 'Marcar fuera de servicio el ascensor 34' }),
    ).toBeDisabled();
  });

  it('avisa cuando no hay reservas para cancelar', async () => {
    renderPage([elevator('33', true, 0)]);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole('button', { name: 'Marcar fuera de servicio el ascensor 33' }),
    );

    expect(screen.getByText(/No hay reservas para cancelar/)).toBeInTheDocument();
  });

  it('reactiva un ascensor fuera de servicio', async () => {
    renderPage([elevator('33', true, 0), elevator('34', false, 0)]);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Reactivar el ascensor 34' }));
    expect(screen.getByText(/Las reservas canceladas no se restauran/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Sí, reactivar' }));

    expect(await screen.findByText('Ascensor 34 en servicio de nuevo.')).toBeInTheDocument();
    expect(screen.queryByText('Fuera de servicio')).not.toBeInTheDocument();
  });

  it('con un 404 muestra el mensaje y actualiza la lista', async () => {
    renderPage([elevator('33', true, 1)], { outStatus: 404 });
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole('button', { name: 'Marcar fuera de servicio el ascensor 33' }),
    );
    expect(
      screen.getByText(/Se cancela 1 reserva sin falta y se avisa a la persona/),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Sí, marcar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ascensor inexistente. Actualizamos la lista.',
    );
  });
});
