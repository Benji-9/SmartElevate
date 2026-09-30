import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedIn, stubApi } from '../test/stubApi';
import type { Departure, FloorOption, Reservation } from '../types/pending';

// 17:32 UTC = 14:32 en Buenos Aires.
const departures: Departure[] = [
  {
    id: 'd-1',
    coreId: 'L1',
    departsAt: '2026-09-29T17:32:00Z',
    durationMinutes: 2,
    capacity: 10,
    occupied: 4,
  },
  {
    id: 'd-2',
    coreId: 'L1',
    departsAt: '2026-09-29T17:34:00Z',
    durationMinutes: 2,
    capacity: 10,
    occupied: 10,
  },
];

const floorsFromPB: FloorOption[] = [
  { floor: -1, eligible: false, reason: 'Para 1 piso usá la escalera.' },
  { floor: 0, eligible: false, reason: 'Es tu piso de origen.' },
  { floor: 1, eligible: false, reason: 'Para 1 piso usá la escalera.' },
  { floor: 5, eligible: true, reason: null },
];

const routes = {
  ...signedIn(),
  'GET /buildings': { body: [{ id: 'LIMA', name: 'Lima', minFloor: -1, maxFloor: 5 }] },
  'GET /cores': {
    body: [
      {
        id: 'L1',
        buildingId: 'LIMA',
        name: 'Lima 1',
        floors: [-2, -1, 0, 1, 5],
        congestion: 'LOW',
        estimatedWaitMinutes: 2,
        hall: 'Hall Lima',
      },
    ],
  },
  'GET /reservations/active': { body: null },
  'GET /cores/L1/departures': { body: departures },
  'GET /cores/L1/floors?origin=0': { body: floorsFromPB },
};

async function renderReserve() {
  render(
    <MemoryRouter initialEntries={['/reservar']}>
      <App />
    </MemoryRouter>,
  );
  await userEvent.click(await screen.findByRole('radio', { name: 'Lima 1' }));
  await userEvent.selectOptions(screen.getByLabelText('Piso de origen'), 'PB');
}

describe('ReserveTurnPage', () => {
  it('con todo elegido reserva y va al turno', async () => {
    const fetchMock = stubApi({
      ...routes,
      'POST /reservations': { body: { id: 'r-9' } },
    });
    await renderReserve();

    // El origen solo ofrece los pisos del núcleo dentro del rango del edificio.
    expect(screen.queryByRole('option', { name: '-2' })).not.toBeInTheDocument();
    const confirm = screen.getByRole('button', { name: /Confirmar turno/ });
    expect(confirm).toBeDisabled();

    await userEvent.click(await screen.findByRole('radio', { name: '5' }));
    await userEvent.click(screen.getByRole('radio', { name: /^14:32/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar turno · 14:32' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Tu turno' })).toBeInTheDocument();
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST' && init.body);
    expect(JSON.parse(post![1]!.body as string)).toEqual({
      departureId: 'd-1',
      originFloor: 0,
      destinationFloor: 5,
    });
  });

  it('muestra las salidas completas deshabilitadas', async () => {
    stubApi(routes);
    await renderReserve();

    const slots = await screen.findByRole('radiogroup', { name: 'Franja horaria' });
    const full = within(slots).getByRole('radio', { name: /^14:34/ });
    expect(full).toBeDisabled();
    expect(full).toHaveAccessibleName('14:34 – 14:36 Completo');
    expect(within(slots).queryByText('10/10')).not.toBeInTheDocument();
    expect(within(slots).getByRole('radio', { name: /^14:32/ })).toHaveAccessibleName(
      expect.stringContaining('4 de 10 lugares ocupados'),
    );
  });

  it('se elige la franja con el teclado', async () => {
    stubApi(routes);
    await renderReserve();

    const slots = await screen.findByRole('radiogroup', { name: 'Franja horaria' });
    within(slots)
      .getByRole('radio', { name: /^14:32/ })
      .focus();
    await userEvent.keyboard(' ');

    expect(within(slots).getByRole('radio', { name: /^14:32/ })).toBeChecked();
  });

  it('muestra los núcleos con nombre corto, en el orden de los edificios', async () => {
    stubApi({
      ...routes,
      'GET /buildings': {
        body: [
          { id: 'LIMA', name: 'Lima', minFloor: -1, maxFloor: 5 },
          { id: 'IND', name: 'Independencia', minFloor: -3, maxFloor: 10 },
        ],
      },
      'GET /cores': {
        body: [
          {
            ...routes['GET /cores'].body[0],
            id: 'IND1',
            buildingId: 'IND',
            name: 'Independencia 1',
          },
          routes['GET /cores'].body[0],
        ],
      },
    });
    render(
      <MemoryRouter initialEntries={['/reservar']}>
        <App />
      </MemoryRouter>,
    );

    const cores = await screen.findByRole('radiogroup', { name: 'Edificio' });
    expect(
      within(cores)
        .getAllByRole('radio')
        .map((r) => r.closest('label')!.textContent),
    ).toEqual(['Lima 1', 'Indep. 1']);
  });

  it('deshabilita el origen y los pisos no elegibles, con el aviso del servidor', async () => {
    stubApi(routes);
    await renderReserve();

    expect(await screen.findByRole('radio', { name: 'PB' })).toBeDisabled();
    expect(screen.getByRole('radio', { name: '1' })).toBeDisabled();
    expect(screen.getByRole('radio', { name: '5' })).toBeEnabled();
    expect(screen.getByText('Para 1 piso usá la escalera.')).toBeInTheDocument();
    expect(screen.getByText('Es tu piso de origen.')).toBeInTheDocument();
  });

  it('muestra el error del servidor al confirmar y recarga las salidas', async () => {
    const fetchMock = stubApi({
      ...routes,
      'POST /reservations': { status: 409, body: { message: 'La salida está llena. Elegí otra.' } },
    });
    await renderReserve();

    await userEvent.click(await screen.findByRole('radio', { name: '5' }));
    await userEvent.click(screen.getByRole('radio', { name: /^14:32/ }));
    await userEvent.click(screen.getByRole('button', { name: /Confirmar turno/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent('La salida está llena. Elegí otra.');
    const departureCalls = () =>
      fetchMock.mock.calls.filter(([url]) => url === '/api/cores/L1/departures').length;
    await expect.poll(departureCalls).toBe(2);
  });

  it('avisa si ya hay un turno activo y ofrece verlo', async () => {
    stubApi({
      ...routes,
      'GET /reservations/active': {
        body: { id: 'r-1', departure: departures[0] } as Partial<Reservation>,
      },
    });
    render(
      <MemoryRouter initialEntries={['/reservar']}>
        <App />
      </MemoryRouter>,
    );

    expect(await screen.findByText(/Ya tenés un turno activo/)).toHaveTextContent('14:32');
    expect(screen.getByRole('link', { name: 'Ver mi turno' })).toHaveAttribute(
      'href',
      '/turno/r-1',
    );
  });
});
