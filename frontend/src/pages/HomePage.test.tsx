import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { signedIn, stubApi } from '../test/stubApi';
import type { CongestionSnapshot, Reservation } from '../types/pending';

const reservation: Reservation = {
  id: 'r-1',
  status: 'ACTIVE',
  departure: {
    id: 'd-1',
    coreId: 'L1',
    departsAt: '2026-09-29T17:32:00Z',
    durationMinutes: 2,
    capacity: 10,
    occupied: 4,
  },
  core: {
    id: 'L1',
    name: 'Lima 1',
    buildingName: 'Lima',
    floors: [0, 1, 2, 3, 4, 5, 6, 7],
    hall: 'Hall Lima, planta baja',
  },
  originFloor: 0,
  destinationFloor: 7,
  cancelCountsAsNoShow: false,
};

const congestion = (): CongestionSnapshot => ({
  updatedAt: new Date(Date.now() - 2 * 60_000).toISOString(),
  refreshAfterSeconds: 30,
  cores: [
    { coreId: 'L1', name: 'Lima 1', level: 'HIGH', estimatedWaitMinutes: 9 },
    { coreId: 'IND2', name: 'Independencia 2', level: 'LOW', estimatedWaitMinutes: 3 },
  ],
});

function renderHome(state?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/', state }]}>
      <App />
    </MemoryRouter>,
  );
}

afterEach(() => {
  vi.useRealTimers();
});

describe('HomePage', () => {
  it('saluda al usuario y muestra su turno activo con el check-in', async () => {
    stubApi({
      ...signedIn(),
      'GET /reservations/active': { body: reservation },
      'GET /congestion': { body: congestion() },
    });
    renderHome();

    expect(await screen.findByRole('heading', { level: 1, name: 'Hola, Ana' })).toBeInTheDocument();
    const card = (await screen.findByRole('heading', { name: 'Tu turno' })).closest('section')!;
    expect(within(card).getByText('Confirmado')).toBeInTheDocument();
    expect(within(card).getByText('14:32 – 14:34')).toBeInTheDocument();
    expect(within(card).getByText('Lima 1 · Lima · Piso 7')).toBeInTheDocument();
    expect(within(card).getByRole('link', { name: 'Hacer check-in' })).toHaveAttribute(
      'href',
      '/check-in',
    );
    expect(within(card).getByRole('link', { name: 'Ver turno' })).toHaveAttribute(
      'href',
      '/turno/r-1',
    );
    expect(screen.queryByRole('link', { name: 'Reservar turno' })).not.toBeInTheDocument();
  });

  it('sin turno activo invita a reservar', async () => {
    stubApi({
      ...signedIn(),
      'GET /reservations/active': { body: null },
      'GET /congestion': { body: congestion() },
    });
    renderHome();

    expect(await screen.findByRole('link', { name: 'Reservar turno' })).toHaveAttribute(
      'href',
      '/reservar',
    );
    expect(screen.queryByRole('link', { name: 'Hacer check-in' })).not.toBeInTheDocument();
  });

  it('muestra la congestión por núcleo con su etiqueta y cuándo se actualizó', async () => {
    stubApi({
      ...signedIn(),
      'GET /reservations/active': { body: null },
      'GET /congestion': { body: congestion() },
    });
    renderHome();

    const section = (await screen.findByRole('heading', { name: 'Congestión ahora' })).closest(
      'section',
    )!;
    const rows = await within(section).findAllByRole('listitem');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('Lima 1');
    expect(rows[0]).toHaveTextContent('Alta');
    expect(rows[1]).toHaveTextContent('Independencia 2');
    expect(rows[1]).toHaveTextContent('Baja');
    expect(within(section).getByText('Actualizado hace 2 min')).toBeInTheDocument();
  });

  it('vuelve a pedir la congestión cuando lo indica el servidor', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const fetchMock = stubApi({
      ...signedIn(),
      'GET /reservations/active': { body: null },
      'GET /congestion': () => ({ body: congestion() }),
    });
    renderHome();
    await screen.findByText('Lima 1');
    const congestionCalls = () =>
      fetchMock.mock.calls.filter(([url]) => url === '/api/congestion').length;
    expect(congestionCalls()).toBe(1);

    await act(() => vi.advanceTimersByTimeAsync(30_000));

    expect(congestionCalls()).toBe(2);
  });

  it('si falla la congestión muestra el error y permite reintentar', async () => {
    let fail = true;
    stubApi({
      ...signedIn(),
      'GET /reservations/active': { body: null },
      'GET /congestion': () =>
        fail
          ? { status: 503, body: { message: 'Servicio no disponible' } }
          : { body: congestion() },
    });
    renderHome();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Servicio no disponible');

    fail = false;
    await userEvent.click(within(alert).getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Independencia 2')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('muestra el aviso que deja otra pantalla, como al cancelar un turno', async () => {
    stubApi({
      ...signedIn(),
      'GET /reservations/active': { body: null },
      'GET /congestion': { body: congestion() },
    });
    renderHome({ notice: 'Cancelaste tu turno.' });

    expect(await screen.findByText('Cancelaste tu turno.')).toHaveAttribute('role', 'status');
  });
});
