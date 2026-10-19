import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { NBSP } from '../features/admin/format';
import { signedIn, stubApi, testUser } from '../test/stubApi';
import type { AdminKpis, AdminShift, Building } from '../types/api';

const buildings: Building[] = [
  { code: 'LIMA', name: 'Lima', minFloor: -2, maxFloor: 10, cores: [] },
  { code: 'INDEPENDENCIA', name: 'Independencia', minFloor: -3, maxFloor: 10, cores: [] },
];

const shifts: AdminShift[] = [
  { id: 'MORNING', name: 'Turno mañana', startsAt: '07:00', endsAt: '13:00' },
  { id: 'AFTERNOON', name: 'Turno tarde', startsAt: '13:00', endsAt: '18:00' },
  { id: 'NIGHT', name: 'Turno noche', startsAt: '18:00', endsAt: '22:30' },
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
  previous: null,
  ...overrides,
});

function renderAdmin(routes = {}) {
  const fetchMock = stubApi({
    ...signedIn({ ...testUser, role: 'ADMIN' }),
    'GET /buildings': { body: buildings },
    'GET /admin/shifts': { body: shifts },
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
  it('muestra las 4 tarjetas de KPIs con su detalle', async () => {
    renderAdmin();

    expect(await screen.findByText('Cargando indicadores…')).toBeInTheDocument();
    const wait = await screen.findByRole('article', { name: 'Espera promedio' });
    expect(wait).toHaveTextContent('3,5 min');
    expect(wait).toHaveTextContent('18 % espera 5–10 min (línea base: 43,5 %)');
    expect(within(wait).getByText('25,5 pts · mejor que la línea base')).toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Turnos reservados' })).toHaveTextContent(
      '1.240hoy',
    );
    expect(screen.getByRole('article', { name: 'Check-ins realizados' })).toHaveTextContent(
      '81 %de los turnos reservados',
    );
    expect(screen.getByRole('article', { name: 'Ocupación promedio' })).toHaveTextContent(
      '6,8 / 10personas por franja',
    );
    expect(screen.getByText('29 de septiembre de 2026 · Todos los edificios')).toBeInTheDocument();
  });

  it('dice si la espera está peor o igual que la línea base, no solo con color', async () => {
    renderAdmin({
      'GET /admin/kpis?period=TODAY': { body: kpis({ wait5To10Percent: 50 }) },
      'GET /admin/kpis?period=WEEK': { body: kpis({ wait5To10Percent: 43.5 }) },
    });

    const wait = await screen.findByRole('article', { name: 'Espera promedio' });
    expect(within(wait).getByText('6,5 pts · peor que la línea base')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: 'Últimos 7 días' }));
    expect(await screen.findByText('Igual que la línea base')).toBeInTheDocument();
  });

  it('el menú lateral solo muestra secciones que existen', async () => {
    renderAdmin();

    const nav = await screen.findByRole('navigation', { name: 'Administración' });
    expect(within(nav).getByRole('link', { name: 'Dashboard' })).toHaveAttribute('href', '/admin');
    // Sin `simulatorEnabled` del servidor no aparece el simulador de kiosco.
    expect(within(nav).getAllByRole('listitem')).toHaveLength(4);
    expect(within(nav).getByRole('link', { name: 'Gestión de usuarios' })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'Kioscos' })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'Ascensores' })).toBeInTheDocument();
    expect(within(nav).queryByText('Próximamente')).not.toBeInTheDocument();
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

  it('filtra por turno de cursada con los turnos que manda el servidor', async () => {
    const fetchMock = renderAdmin({
      'GET /admin/kpis?period=TODAY&shiftId=MORNING': { body: kpis({ reservations: 700 }) },
    });

    const group = await screen.findByRole('radiogroup', { name: 'Turno de cursada' });
    expect(within(group).getByRole('radio', { name: 'Todo el día' })).toBeChecked();
    await userEvent.click(await within(group).findByRole('radio', { name: 'Turno mañana' }));

    expect(
      await screen.findByText(
        '29 de septiembre de 2026 · Todos los edificios · Turno mañana (7:00–13:00)',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Turnos reservados' })).toHaveTextContent('700');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/kpis?period=TODAY&shiftId=MORNING',
      expect.anything(),
    );
  });

  it('ofrece los tres turnos y avisa cuál es el actual', async () => {
    // 15:30 en Buenos Aires.
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-09-29T18:30:00Z') });
    try {
      renderAdmin();

      const group = await screen.findByRole('radiogroup', { name: 'Turno de cursada' });
      for (const name of ['Todo el día', 'Turno mañana', 'Turno tarde', 'Turno noche']) {
        expect(await within(group).findByRole('radio', { name })).toBeInTheDocument();
      }
      expect(await screen.findByText('Turno tarde (13:00–18:00)')).toBeInTheDocument();
      expect(screen.getByText(/Ahora:/)).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it('fuera del horario de los turnos no muestra el aviso', async () => {
    // 23:00 en Buenos Aires.
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-09-30T02:00:00Z') });
    try {
      renderAdmin();

      await screen.findByRole('radiogroup', { name: 'Turno de cursada' });
      await screen.findByRole('radio', { name: 'Turno noche' });
      expect(screen.queryByText(/Ahora:/)).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it('en pantallas angostas avisa que el panel es para computadora', async () => {
    // jsdom no evalúa container queries: el aviso siempre está en el DOM y el CSS
    // (@container app) decide si se ve el aviso o el panel.
    renderAdmin();

    expect(
      await screen.findByRole('heading', {
        name: 'El panel de administración es para computadora',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Abrilo desde una pantalla más grande para ver el detalle de congestión.'),
    ).toBeInTheDocument();
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

  it('compara con el período anterior de igual largo', async () => {
    renderAdmin({
      'GET /admin/kpis?period=TODAY': {
        body: kpis({
          previous: {
            from: '2026-09-28T03:00:00Z',
            to: '2026-09-29T03:00:00Z',
            reservations: 1000,
            checkInPercent: 81,
            avgOccupancy: 8.5,
            avgWaitSeconds: null,
            wait5To10Percent: null,
          },
        }),
      },
    });

    expect(await screen.findByRole('article', { name: 'Turnos reservados' })).toHaveTextContent(
      'Subió 24 % vs. ayer',
    );
    expect(screen.getByRole('article', { name: 'Check-ins realizados' })).toHaveTextContent(
      'Igual que ayer',
    );
    expect(screen.getByRole('article', { name: 'Ocupación promedio' })).toHaveTextContent(
      'Bajó 20 % vs. ayer',
    );
  });

  it('los KPIs sin datos dicen "Sin datos" y no se comparan', async () => {
    renderAdmin({
      'GET /admin/kpis?period=TODAY': {
        body: kpis({
          avgWaitSeconds: null,
          wait5To10Percent: null,
          checkInPercent: null,
          avgOccupancy: null,
          cores: [
            {
              coreId: 'L1',
              name: 'Lima 1',
              reservations: 4,
              occupancyPercent: null,
              avgWaitSeconds: null,
              congestion: null,
            },
          ],
        }),
      },
    });

    const wait = await screen.findByRole('article', { name: 'Espera promedio' });
    expect(wait).toHaveTextContent('Sin datos');
    expect(wait).toHaveTextContent('Sin datos de la encuesta de espera');
    expect(within(wait).queryByText(/línea base$/)).not.toBeInTheDocument();
    expect(screen.getByRole('article', { name: 'Check-ins realizados' })).toHaveTextContent(
      'Sin datos',
    );
    expect(screen.getByRole('article', { name: 'Ocupación promedio' })).toHaveTextContent(
      'Sin datos',
    );
    const table = screen.getByRole('table', { name: 'Estado por núcleo' });
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent(
      'Lima 14Sin datosSin datosSin datos',
    );
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
