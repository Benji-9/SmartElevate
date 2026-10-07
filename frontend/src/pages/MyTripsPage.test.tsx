import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedIn, stubApi } from '../test/stubApi';
import type { NoShowStatus, Trip, TripPage } from '../types/pending';

// 29/9 17:04 UTC = 14:04 en Buenos Aires; 30/9 01:30 UTC sigue siendo el 29/9 (22:30) allá.
const trip = (id: string, departsAt: string, result: Trip['result']): Trip => ({
  id,
  departsAt,
  durationMinutes: 2,
  coreName: 'Lima 1',
  originFloor: 0,
  destinationFloor: 7,
  result,
});

const trips: Trip[] = [
  trip('t-1', '2026-09-30T01:30:00Z', 'COMPLETED'),
  trip('t-2', '2026-09-29T17:04:00Z', 'OTHER_ELEVATOR'),
  trip('t-3', '2026-09-28T13:00:00Z', 'LATE'),
  trip('t-4', '2026-09-28T12:00:00Z', 'CANCELLED'),
  trip('t-5', '2026-09-27T12:00:00Z', 'NO_SHOW'),
];

const noShows = (status: Partial<NoShowStatus> = {}): NoShowStatus => ({
  recentNoShows: 0,
  threshold: 3,
  windowDays: 7,
  suspensionHours: 24,
  suspendedUntil: null,
  exempt: false,
  ...status,
});

function renderTrips(
  page: TripPage = { items: trips, nextCursor: null },
  status: NoShowStatus = noShows(),
  extra = {},
) {
  const fetchMock = stubApi({
    ...signedIn(),
    'GET /reservations/history': { body: page },
    'GET /me/no-shows': { body: status },
    ...extra,
  });
  render(
    <MemoryRouter initialEntries={['/perfil/viajes']}>
      <App />
    </MemoryRouter>,
  );
  return fetchMock;
}

describe('MyTripsPage', () => {
  it('lista los viajes agrupados por día (hora de Buenos Aires) con su resultado', async () => {
    renderTrips();

    expect(await screen.findByText('Cargando tus viajes…')).toBeInTheDocument();
    const today = await screen.findByRole('region', { name: 'martes, 29 de septiembre' });
    expect(within(today).getAllByRole('listitem')).toHaveLength(2);
    expect(within(today).getByText('22:30 – 22:32')).toBeInTheDocument();
    expect(within(today).getByText('Cumplido')).toBeInTheDocument();
    expect(within(today).getByText('Otro ascensor')).toBeInTheDocument();

    const monday = screen.getByRole('region', { name: 'lunes, 28 de septiembre' });
    const late = within(monday).getByText('Falta (fuera de hora)');
    expect(within(monday).getByText('Cancelado')).toBeInTheDocument();

    const sunday = screen.getByRole('region', { name: 'domingo, 27 de septiembre' });
    // Fuera de hora cuenta como falta: mismo tono que "Falta" (#154).
    expect(late).toHaveClass(within(sunday).getByText('Falta').className, { exact: true });
    expect(within(sunday).getByRole('listitem')).toHaveTextContent('Lima 1 · PB → a 7');

    expect(screen.queryByRole('button', { name: 'Ver más' })).not.toBeInTheDocument();
  });

  it('"Volver" lleva al perfil', async () => {
    renderTrips();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Mis viajes' }),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Volver' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Mi perfil' })).toBeInTheDocument();
  });

  it('muestra el estado vacío', async () => {
    renderTrips({ items: [], nextCursor: null });

    expect(await screen.findByText(/Todavía no tenés viajes/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Reservá tu primer turno' })).toHaveAttribute(
      'href',
      '/reservar',
    );
  });

  it('muestra el error y permite reintentar', async () => {
    let calls = 0;
    renderTrips(undefined, undefined, {
      'GET /reservations/history': () =>
        ++calls === 1
          ? { status: 500, body: { message: 'Error inesperado' } }
          : { body: { items: trips, nextCursor: null } },
    });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Error inesperado');
    await userEvent.click(within(alert).getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Cumplido')).toBeInTheDocument();
  });

  it('"Ver más" pide la página siguiente y la agrega al final', async () => {
    const fetchMock = renderTrips({ items: trips.slice(0, 2), nextCursor: 'c-2' }, undefined, {
      'GET /reservations/history?cursor=c-2': { body: { items: trips.slice(2), nextCursor: null } },
    });

    await userEvent.click(await screen.findByRole('button', { name: 'Ver más' }));

    expect(await screen.findByText('Falta')).toBeInTheDocument();
    expect(screen.getAllByText(/Lima 1/)).toHaveLength(5);
    const days = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent);
    expect(days).toEqual([
      'martes, 29 de septiembre',
      'lunes, 28 de septiembre',
      'domingo, 27 de septiembre',
    ]);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/reservations/history?cursor=c-2',
      expect.anything(),
    );
    expect(screen.queryByRole('button', { name: 'Ver más' })).not.toBeInTheDocument();
  });

  it('no muestra aviso si no hay faltas recientes', async () => {
    renderTrips();

    await screen.findByText('Cumplido');
    expect(screen.queryByRole('region', { name: /faltas|reservar/ })).not.toBeInTheDocument();
  });

  it('avisa las faltas recientes con los parámetros de la regla', async () => {
    renderTrips(
      undefined,
      noShows({ recentNoShows: 2, threshold: 4, windowDays: 10, suspensionHours: 48 }),
    );

    const notice = await screen.findByRole('region', { name: 'Tenés faltas' });
    expect(notice).toHaveTextContent('Tenés 2 faltas en los últimos 10 días.');
    expect(notice).toHaveTextContent('Con 4 faltas en 10 días no vas a poder reservar por 48 h.');
  });

  it('avisa la suspensión y hasta cuándo dura', async () => {
    renderTrips(undefined, noShows({ recentNoShows: 3, suspendedUntil: '2026-09-30T20:00:00Z' }));

    const notice = await screen.findByRole('region', { name: 'No podés reservar por ahora' });
    expect(notice).toHaveTextContent(
      'Vas a poder volver a reservar el miércoles, 30 de septiembre a las 17:00.',
    );
  });

  it('a un usuario prioritario no le muestra la amenaza de suspensión', async () => {
    renderTrips(undefined, noShows({ recentNoShows: 3, exempt: true }));

    const notice = await screen.findByRole('region', { name: 'Tenés faltas' });
    expect(notice).toHaveTextContent('Tenés 3 faltas en los últimos 7 días.');
    expect(notice).not.toHaveTextContent(/no vas a poder reservar/i);
  });
});
