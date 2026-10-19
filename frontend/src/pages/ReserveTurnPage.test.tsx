import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedIn, stubApi, testUser } from '../test/stubApi';
import type { Building, Departure, Elevator, Reservation } from '../types/api';

const lima: Building = {
  code: 'LIMA',
  name: 'Lima',
  minFloor: -1,
  maxFloor: 5,
  cores: [{ code: 'L1', name: 'Lima 1' }],
};

const elevator = (code: string, floors: number[], core = 'L1'): Elevator => ({
  code,
  building: 'LIMA',
  core,
  coreName: 'Lima 1',
  floors,
  usage: 'COMMON',
  active: true,
});
// Dos baterías: la 01 no para en el 3 y la 02 no para en el 1 ni en el 5.
const elevators = [elevator('01', [-2, -1, 0, 1, 5]), elevator('02', [-1, 0, 3])];

// 17:32 UTC = 14:32 en Buenos Aires.
const departure = (id: number, code: string, at: string, remainingSeats: number): Departure => ({
  id,
  elevator: code,
  building: 'LIMA',
  departureAt: `2026-09-29T${at}:00Z`,
  capacity: 10,
  remainingSeats,
  status: 'SCHEDULED',
});
const departures = [
  departure(1, '01', '17:32', 6),
  departure(2, '01', '17:34', 0),
  departure(3, '02', '17:32', 3),
];

const reservation = (overrides: Partial<Reservation> = {}): Reservation => ({
  id: 9,
  departureId: 1,
  elevator: '01',
  coreName: 'Lima 1',
  buildingName: 'Lima',
  departureAt: '2026-09-29T17:32:00Z',
  cancelFreeUntil: '2026-09-29T17:22:00Z',
  seatNumber: 5,
  originFloor: 0,
  destinationFloor: 5,
  status: 'ACTIVE',
  createdAt: '2026-09-29T17:10:00Z',
  cancelCountsAsNoShow: false,
  checkInOutcome: null,
  ...overrides,
});

const routes = {
  ...signedIn(),
  'GET /buildings': { body: [lima] },
  'GET /elevators': { body: elevators },
  'GET /reservations/active': { status: 204 },
  'GET /departures?building=LIMA': { body: departures },
};

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/reservar']}>
      <App />
    </MemoryRouter>,
  );

async function renderReserve() {
  renderPage();
  await userEvent.click(await screen.findByRole('radio', { name: 'Lima 1' }));
  await userEvent.selectOptions(screen.getByLabelText('Piso de origen'), 'PB');
}

/** Elige PB → 5 y la salida de las 14:32, y confirma. */
async function reserveAt1432() {
  await renderReserve();
  await userEvent.click(screen.getByRole('radio', { name: '5' }));
  await userEvent.click(await screen.findByRole('radio', { name: /^14:32/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar turno · 14:32' }));
}

const rejection = (status: number, code: string, message: string) => ({
  status,
  body: { status, code, message, error: 'Conflict', path: '/api/reservations' },
});

describe('ReserveTurnPage', () => {
  it('con todo elegido reserva y va al turno', async () => {
    const fetchMock = stubApi({ ...routes, 'POST /reservations': { body: reservation() } });
    await renderReserve();

    // El origen solo ofrece los pisos de los ascensores dentro del rango del edificio.
    expect(screen.queryByRole('option', { name: '-2' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Confirmar turno/ })).toBeDisabled();

    await userEvent.click(screen.getByRole('radio', { name: '5' }));
    await userEvent.click(await screen.findByRole('radio', { name: /^14:32/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar turno · 14:32' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Tu turno' })).toBeInTheDocument();
    const post = fetchMock.mock.calls.find(
      ([url, init]) => url === '/api/reservations' && init?.method === 'POST',
    );
    expect(JSON.parse(post![1]!.body as string)).toEqual({
      departureId: 1,
      originFloor: 0,
      destinationFloor: 5,
    });
    // Queda guardado para precargar la próxima reserva (sin la franja).
    expect(JSON.parse(localStorage.getItem('se:lastTrip')!)).toEqual({
      coreId: 'L1',
      originFloor: 0,
      destinationFloor: 5,
    });
  });

  it('al pie muestra lo elegido y qué falta, asociado al botón', async () => {
    stubApi(routes);
    renderPage();
    const confirm = async () => screen.findByRole('button', { name: /Confirmar turno/ });

    expect(await confirm()).toHaveAccessibleDescription('Falta elegir el edificio');

    await userEvent.click(screen.getByRole('radio', { name: 'Lima 1' }));
    expect(await confirm()).toHaveAccessibleDescription('Falta elegir el piso de origen');
    expect(screen.getByText('Lima 1', { selector: 'p' })).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Piso de origen'), 'PB');
    await userEvent.click(await screen.findByRole('radio', { name: /^14:32/ }));
    expect(await confirm()).toHaveAccessibleDescription('Falta elegir el piso de destino');

    await userEvent.click(screen.getByRole('radio', { name: '5' }));
    expect(await confirm()).toBeEnabled();
    expect(await confirm()).not.toHaveAccessibleDescription();
    expect(screen.getByText('Lima 1 · Piso 5 · 14:32')).toBeInTheDocument();
  });

  it('agrupa por hora, ofrece el ascensor con más lugar y deshabilita las completas', async () => {
    stubApi(routes);
    await renderReserve();

    const slots = await screen.findByRole('radiogroup', { name: 'Franja horaria' });
    // A las 14:32 salen el 01 (6 libres) y el 02 (3): se ofrece el 01.
    expect(within(slots).getAllByRole('radio')).toHaveLength(2);
    expect(within(slots).getByRole('radio', { name: /^14:32/ })).toHaveAccessibleName(
      '14:32 · Ascensor 01 6 libres',
    );
    const full = within(slots).getByRole('radio', { name: /^14:34/ });
    expect(full).toBeDisabled();
    expect(full).toHaveAccessibleName('14:34 · Ascensor 01 Completo');
  });

  it('los destinos y las salidas salen de los ascensores que paran en los pisos elegidos', async () => {
    stubApi(routes);
    await renderReserve();

    // Desde PB paran los dos: destinos de ambas baterías, sin el origen.
    const destinations = () =>
      within(screen.getByRole('radiogroup', { name: 'Piso de destino' }))
        .getAllByRole('radio')
        .map((r) => r.closest('label')!.textContent);
    expect(destinations()).toEqual(['-1', '1', '3', '5']);

    // Al 3 solo va el 02: sus salidas, nada más.
    await userEvent.click(screen.getByRole('radio', { name: '3' }));
    const slots = screen.getByRole('radiogroup', { name: 'Franja horaria' });
    expect(
      within(slots)
        .getAllByRole('radio')
        .map((r) => r.closest('label')!.textContent),
    ).toEqual(['14:32 · Ascensor 02 3 libres']);

    // Desde el 1 solo para el 01: el 3 deja de ser destino.
    await userEvent.selectOptions(screen.getByLabelText('Piso de origen'), '1');
    expect(destinations()).toEqual(['-1', 'PB', '5']);
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

  describe('núcleos disponibles', () => {
    const independencia: Building = {
      code: 'INDEPENDENCIA',
      name: 'Independencia',
      minFloor: -3,
      maxFloor: 10,
      cores: [{ code: 'IND1', name: 'Independencia 1' }],
    };
    const dedicated = (code: string, usage: Elevator['usage']): Elevator => ({
      ...elevator(code, [0, 5], 'IND1'),
      building: 'INDEPENDENCIA',
      usage,
    });
    const catalogRoutes = (priority: 'NONE' | 'TEACHER' | 'REDUCED_MOBILITY', extra = {}) => ({
      ...routes,
      ...signedIn({ ...testUser, priority }),
      'GET /buildings': {
        body: [{ ...lima, cores: [...lima.cores, { code: 'L2', name: 'Lima 2' }] }, independencia],
      },
      'GET /elevators': {
        body: [
          dedicated('05', 'TEACHERS'),
          dedicated('06', 'REDUCED_MOBILITY'),
          ...elevators,
          // L2 sin ascensores activos: no se ofrece.
          { ...elevator('07', [0, 5], 'L2'), active: false },
        ],
      },
      ...extra,
    });
    const offered = async () => {
      const cores = await screen.findByRole('radiogroup', { name: 'Edificio' });
      return within(cores)
        .getAllByRole('radio')
        .map((r) => r.closest('label')!.textContent);
    };

    it('un usuario común no ve Independencia 1: sus ascensores son dedicados', async () => {
      stubApi(catalogRoutes('NONE'));
      renderPage();

      expect(await offered()).toEqual(['Lima 1']);
    });

    it('un docente con prioridad vigente ve Independencia 1, con nombre corto y en orden', async () => {
      stubApi(catalogRoutes('TEACHER'));
      renderPage();

      expect(await offered()).toEqual(['Lima 1', 'Indep. 1']);
    });

    it('movilidad reducida también lo ve', async () => {
      stubApi(catalogRoutes('REDUCED_MOBILITY'));
      renderPage();

      expect(await offered()).toEqual(['Lima 1', 'Indep. 1']);
    });

    it('un núcleo con ascensores comunes y dedicados se ofrece, pero solo con los que puede usar', async () => {
      stubApi(
        catalogRoutes('NONE', {
          'GET /elevators': {
            body: [
              { ...dedicated('05', 'TEACHERS'), floors: [-3, 0, 10] },
              dedicated('08', 'COMMON'),
            ],
          },
        }),
      );
      renderPage();

      expect(await offered()).toEqual(['Indep. 1']);
      await userEvent.click(screen.getByRole('radio', { name: 'Indep. 1' }));
      // Los pisos del ascensor dedicado ajeno (-3 y 10) no cuentan para el origen.
      expect(screen.queryByRole('option', { name: '-3' })).not.toBeInTheDocument();
    });

    it('no precarga un último viaje a un núcleo que el usuario ya no puede usar', async () => {
      localStorage.setItem(
        'se:lastTrip',
        JSON.stringify({ coreId: 'IND1', originFloor: 0, destinationFloor: 5 }),
      );
      stubApi(catalogRoutes('NONE'));
      renderPage();

      expect(await offered()).toEqual(['Lima 1']);
      expect(screen.getByRole('radio', { name: 'Lima 1' })).not.toBeChecked();
    });
  });

  it('sin salidas que pueda reservar (p. ej. un ascensor dedicado ajeno) lo dice', async () => {
    stubApi({ ...routes, 'GET /departures?building=LIMA': { body: [] } });
    await renderReserve();

    expect(
      await screen.findByText(/No hay salidas que puedas reservar en este núcleo/),
    ).toBeInTheDocument();
    expect(screen.queryByRole('radiogroup', { name: 'Franja horaria' })).not.toBeInTheDocument();
  });

  it('si no cargan las salidas muestra el error', async () => {
    stubApi({
      ...routes,
      'GET /departures?building=LIMA': { status: 500, body: { message: 'Error interno' } },
    });
    await renderReserve();

    expect(await screen.findByRole('alert')).toHaveTextContent('Error interno');
  });

  describe('rechazos al confirmar, por code', () => {
    it.each([
      ['TURN_FULL', 409, 'La salida está llena. Elegí otra.'],
      [
        'TURN_FULL',
        409,
        'Los lugares que quedan están reservados para prioritarios. Elegí otra salida.',
      ],
      ['ELEVATOR_NOT_BOOKABLE', 422, 'Ese ascensor es exclusivo para docentes.'],
      ['OUTSIDE_BOOKING_WINDOW', 409, 'La salida está fuera de la ventana de reserva.'],
      ['DEPARTURE_NOT_FOUND', 404, 'La salida no existe.'],
    ])(
      '%s: muestra el motivo, recarga las salidas y lleva el foco para elegir otra',
      async (code, status, message) => {
        let departureCalls = 0;
        stubApi({
          ...routes,
          // Al recargar, al 01 de las 14:32 (el único que va al 5) no le queda lugar.
          'GET /departures?building=LIMA': () => ({
            body: departureCalls++
              ? [departure(1, '01', '17:32', 0), ...departures.slice(1)]
              : departures,
          }),
          'POST /reservations': rejection(status, code, message),
        });
        await reserveAt1432();

        expect(await screen.findByRole('alert')).toHaveTextContent(message);
        expect(screen.getByText('Franja horaria')).toHaveFocus();
        expect(
          await screen.findByRole('radio', { name: '14:32 · Ascensor 01 Completo' }),
        ).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Confirmar turno' })).toHaveAccessibleDescription(
          'Falta elegir la franja horaria',
        );
      },
    );

    it.each([
      ['SAME_FLOOR', 'El piso de origen y el de destino son el mismo.'],
      ['FLOOR_OUT_OF_RANGE', 'Alguno de los pisos no existe en el edificio.'],
      ['FLOOR_NOT_SERVED', 'Ese ascensor no para en alguno de los pisos.'],
      ['LOW_FLOOR_DESTINATION', 'Para ir a los pisos 1 a 4 usá la escalera.'],
    ])('%s: muestra el motivo del servidor y pide otro destino', async (code, message) => {
      stubApi({ ...routes, 'POST /reservations': rejection(422, code, message) });
      await reserveAt1432();

      expect(await screen.findByRole('alert')).toHaveTextContent(message);
      expect(screen.getByRole('radio', { name: '5' })).not.toBeChecked();
      expect(screen.getByRole('button', { name: /Confirmar turno/ })).toHaveAccessibleDescription(
        'Falta elegir el piso de destino',
      );
    });

    it('NO_SHOW_SUSPENDED: dice hasta cuándo no puede reservar, como lo manda el servidor', async () => {
      const message =
        'Por tus faltas de los últimos días no podés reservar hasta el 30/09 a las 14:32.';
      stubApi({ ...routes, 'POST /reservations': rejection(409, 'NO_SHOW_SUSPENDED', message) });
      await reserveAt1432();

      expect(await screen.findByRole('alert')).toHaveTextContent(message);
      // Lo elegido queda: no es un problema de la salida ni de los pisos.
      expect(screen.getByRole('radio', { name: /^14:32/ })).toBeChecked();
    });

    it('DAILY_LIMIT_REACHED: muestra el tope del día', async () => {
      stubApi({
        ...routes,
        'POST /reservations': rejection(
          409,
          'DAILY_LIMIT_REACHED',
          'Llegaste al tope de reservas de hoy.',
        ),
      });
      await reserveAt1432();

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Llegaste al tope de reservas de hoy.',
      );
    });

    it('ACTIVE_LIMIT_REACHED: ya tiene un turno (p. ej. de otro dispositivo) y lo ofrece', async () => {
      let mine = 0;
      stubApi({
        ...routes,
        'GET /reservations/active': () =>
          mine++ ? { body: reservation({ id: 7 }) } : { status: 204 },
        'POST /reservations': rejection(
          409,
          'ACTIVE_LIMIT_REACHED',
          'Ya tenés una reserva activa.',
        ),
      });
      await reserveAt1432();

      expect(await screen.findByRole('link', { name: 'Ver mi turno' })).toHaveAttribute(
        'href',
        '/turno/7',
      );
    });

    it('sin code (p. ej. sin conexión) muestra un mensaje genérico y deja lo elegido', async () => {
      stubApi({
        ...routes,
        'POST /reservations': () => {
          throw new TypeError('Failed to fetch');
        },
      });
      await reserveAt1432();

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'No pudimos conectarnos. Probá de nuevo.',
      );
      expect(screen.getByRole('radio', { name: /^14:32/ })).toBeChecked();
    });
  });

  it('con un turno activo no ofrece el formulario y lleva al turno', async () => {
    stubApi({
      ...routes,
      'GET /reservations/active': { body: reservation({ id: 7 }) },
    });
    renderPage();

    expect(await screen.findByText(/Ya tenés un turno activo/)).toHaveTextContent('14:32');
    expect(screen.getByRole('link', { name: 'Ver mi turno' })).toHaveAttribute('href', '/turno/7');
    expect(screen.queryByRole('radiogroup', { name: 'Edificio' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Confirmar turno/ })).not.toBeInTheDocument();
  });

  it('si no se puede saber si hay un turno activo, muestra el formulario igual', async () => {
    stubApi({
      ...routes,
      'GET /reservations/active': { status: 500, body: { message: 'Error interno' } },
    });
    renderPage();

    expect(await screen.findByRole('radiogroup', { name: 'Edificio' })).toBeInTheDocument();
    expect(screen.queryByText(/Ya tenés un turno activo/)).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  describe('precarga del último viaje', () => {
    const saveTrip = (trip: unknown) => localStorage.setItem('se:lastTrip', JSON.stringify(trip));

    it('abre con el núcleo y los pisos del último viaje; solo falta la franja', async () => {
      saveTrip({ coreId: 'L1', originFloor: 0, destinationFloor: 5 });
      stubApi(routes);
      renderPage();

      expect(await screen.findByRole('radio', { name: '5' })).toBeChecked();
      expect(screen.getByRole('radio', { name: 'Lima 1' })).toBeChecked();
      expect(screen.getByLabelText('Piso de origen')).toHaveDisplayValue('PB');
      expect(await screen.findByRole('radio', { name: /^14:32/ })).not.toBeChecked();
      expect(screen.getByText(/Usamos tu último viaje/)).toHaveTextContent(
        'Usamos tu último viaje: Lima 1 · PB → 5. Podés cambiarlo abajo.',
      );
      expect(screen.getByRole('button', { name: 'Confirmar turno' })).toHaveAccessibleDescription(
        'Falta elegir la franja horaria',
      );

      // Se puede cambiar: al tocar otro piso el aviso se va.
      await userEvent.click(screen.getByRole('radio', { name: '-1' }));
      expect(screen.getByRole('radio', { name: '-1' })).toBeChecked();
      expect(screen.queryByText(/Usamos tu último viaje/)).not.toBeInTheDocument();
    });

    it('ignora sin error un destino que ya no sirve ningún ascensor', async () => {
      saveTrip({ coreId: 'L1', originFloor: 0, destinationFloor: 4 });
      stubApi(routes);
      renderPage();

      expect(await screen.findByLabelText('Piso de origen')).toHaveDisplayValue('PB');
      expect(screen.queryByRole('radio', { name: '4' })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Confirmar turno' })).toHaveAccessibleDescription(
        'Falta elegir el piso de destino',
      );
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('ignora un núcleo que ya no existe y abre vacío', async () => {
      saveTrip({ coreId: 'X9', originFloor: 0, destinationFloor: 5 });
      const fetchMock = stubApi(routes);
      renderPage();

      expect(await screen.findByRole('radio', { name: 'Lima 1' })).not.toBeChecked();
      expect(screen.queryByText(/Usamos tu último viaje/)).not.toBeInTheDocument();
      expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/departures'))).toBe(false);
    });

    it('sin viaje guardado, o con un valor roto, abre vacío', async () => {
      localStorage.setItem('se:lastTrip', '{roto');
      stubApi(routes);
      renderPage();

      expect(await screen.findByRole('radio', { name: 'Lima 1' })).not.toBeChecked();
      expect(screen.queryByText(/Usamos tu último viaje/)).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Confirmar turno' })).toHaveAccessibleDescription(
        'Falta elegir el edificio',
      );
    });
  });
});
