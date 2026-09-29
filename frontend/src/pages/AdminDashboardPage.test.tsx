import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { NBSP } from '../features/admin/format';
import { signedIn, stubApi, testUser } from '../test/stubApi';
import type { AdminKpis, Building } from '../types/pending';

const buildings: Building[] = [
  { id: 'LIMA', name: 'Lima', minFloor: -2, maxFloor: 10 },
  { id: 'IND', name: 'Independencia', minFloor: -3, maxFloor: 10 },
];

// 29/9 03:00 UTC = 00:00 en Buenos Aires.
const kpis = (overrides: Partial<AdminKpis> = {}): AdminKpis => ({
  from: '2026-09-29T03:00:00Z',
  to: '2026-09-30T03:00:00Z',
  avgWaitSeconds: 210,
  wait5To10Percent: 18,
  baselinePercent: 43.5,
  reservations: 1240,
  checkIns: 1004,
  checkInPercent: 81,
  avgOccupancy: 6.8,
  capacity: 10,
  reservationsByHour: [
    { hour: 8, reservations: 40 },
    { hour: 9, reservations: 120 },
  ],
  cores: [
    {
      coreId: 'L1',
      name: 'Lima 1',
      reservations: 380,
      occupancyPercent: 92,
      avgWaitSeconds: 420,
      congestion: 'HIGH',
    },
    {
      coreId: 'L3',
      name: 'Lima 3',
      reservations: 120,
      occupancyPercent: 41,
      avgWaitSeconds: 90,
      congestion: 'LOW',
    },
  ],
  ...overrides,
});

function renderAdmin(routes = {}) {
  const fetchMock = stubApi({
    ...signedIn({ ...testUser, role: 'ADMIN' }),
    'GET /buildings': { body: buildings },
    'GET /admin/kpis?period=TODAY': { body: kpis() },
    ...routes,
  });
  render(
    <MemoryRouter initialEntries={['/admin']}>
      <App />
    </MemoryRouter>,
  );
  return fetchMock;
}

describe('AdminDashboardPage', () => {
  it('muestra las 4 tarjetas de KPIs contra la línea base', async () => {
    renderAdmin();

    expect(await screen.findByText('Cargando indicadores…')).toBeInTheDocument();
    const wait = await screen.findByRole('article', { name: 'Espera promedio' });
    expect(wait).toHaveTextContent('3,5 min');
    expect(wait).toHaveTextContent('18 % espera 5–10 min (línea base: 43,5 %)');
    expect(screen.getByRole('article', { name: 'Turnos reservados' })).toHaveTextContent('1.240');
    const checkIns = screen.getByRole('article', { name: 'Check-ins realizados' });
    expect(checkIns).toHaveTextContent('81 %');
    expect(checkIns).toHaveTextContent('1.004 de 1.240 turnos');
    expect(screen.getByRole('article', { name: 'Ocupación promedio' })).toHaveTextContent(
      '6,8 / 10',
    );
    expect(screen.getByText('29 de septiembre de 2026 · Todas las sedes')).toBeInTheDocument();
  });

  it('el gráfico tiene una tabla equivalente para lectores de pantalla', async () => {
    renderAdmin();

    const chart = await screen.findByRole('figure', { name: 'Reservas por franja' });
    const table = within(chart).getByRole('table', { name: 'Reservas por hora del día' });
    expect(within(table).getByRole('row', { name: '9:00 a 10:00 120' })).toBeInTheDocument();
  });

  it('muestra el estado por núcleo con su nivel en texto', async () => {
    renderAdmin();

    const table = await screen.findByRole('table', { name: 'Estado por núcleo' });
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows.map((row) => row.textContent)).toEqual([
      `Lima 138092${NBSP}%7 minAlta`,
      `Lima 312041${NBSP}%1,5 minBaja`,
    ]);
  });

  it('cambiar los filtros vuelve a pedir los KPIs del período y la sede', async () => {
    const fetchMock = renderAdmin({
      'GET /admin/kpis?period=WEEK': {
        body: kpis({ from: '2026-09-23T03:00:00Z', reservations: 8400 }),
      },
      'GET /admin/kpis?period=WEEK&buildingId=LIMA': {
        body: kpis({ from: '2026-09-23T03:00:00Z', reservations: 5100 }),
      },
    });

    await screen.findByRole('article', { name: 'Turnos reservados' });
    await userEvent.click(screen.getByRole('radio', { name: 'Últimos 7 días' }));
    expect(await screen.findByRole('article', { name: 'Turnos reservados' })).toHaveTextContent(
      '8.400',
    );

    await userEvent.click(screen.getByRole('radio', { name: 'Lima' }));
    expect(await screen.findByText('23–29 de septiembre de 2026 · Lima')).toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Turnos reservados' })).toHaveTextContent('5.100');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/kpis?period=WEEK&buildingId=LIMA',
      expect.anything(),
    );
  });

  it('muestra el error y permite reintentar', async () => {
    let calls = 0;
    renderAdmin({
      'GET /admin/kpis?period=TODAY': () =>
        ++calls === 1 ? { status: 500, body: { message: 'Error inesperado' } } : { body: kpis() },
    });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Error inesperado');
    await userEvent.click(within(alert).getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('table', { name: 'Estado por núcleo' })).toBeInTheDocument();
  });

  it('sin reservas en el período muestra el estado vacío', async () => {
    renderAdmin({
      'GET /admin/kpis?period=TODAY': {
        body: kpis({ reservations: 0, checkIns: 0, checkInPercent: 0 }),
      },
    });

    expect(await screen.findByText('No hubo reservas en este período.')).toBeInTheDocument();
    expect(screen.queryByRole('figure')).not.toBeInTheDocument();
  });
});
