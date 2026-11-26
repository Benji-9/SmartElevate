import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  AdminElevator,
  AdminKpis,
  AdminShift,
  Building,
  CheckIn,
  CongestionSnapshot,
  CreatedKiosk,
  Departure,
  Elevator,
  ElevatorServiceResult,
  KioskList,
  NoShowStatus,
  NotificationPreferences,
  QrToken,
  Reservation,
  ReservationPage,
} from '../types/api';
import { ApiError } from './api';
import { mockRequest } from './mocks';

const call = async <T>(method: string, path: string, body?: unknown, headers?: HeadersInit) =>
  (
    await mockRequest(
      method,
      path,
      body === undefined ? undefined : JSON.stringify(body),
      0,
      headers,
    )
  )?.data as T;

const loginAna = () =>
  call('POST', '/auth/login', { email: 'ana.perez@uade.edu.ar', password: 'x' });
/** Cancela el turno activo, si hay, para poder reservar otro. */
async function cancelActive() {
  const active = await call<Reservation | undefined>('GET', '/reservations/active');
  if (active) await call('DELETE', `/reservations/${active.id}`);
}
const MIN = 60_000;
/** Con lugar y a más de 10 min: cancelarla todavía es sin falta. */
const freeDeparture = async (query = 'building=INDEPENDENCIA') =>
  (await call<Departure[]>('GET', `/departures?${query}`)).find(
    (d) => d.remainingSeats > 0 && Date.parse(d.departureAt) - Date.now() > 11 * MIN,
  )!;
const reserveOn = (d: Departure) =>
  call<Reservation>('POST', '/reservations', {
    departureId: d.id,
    originFloor: 0,
    destinationFloor: 8,
  });

afterEach(() => {
  vi.useRealTimers();
  vi.resetModules();
});

describe('mocks', () => {
  it('deja pasar al backend lo que no tiene mock', async () => {
    // Ninguna pantalla lo usa.
    expect(await mockRequest('GET', '/ping', undefined, 0)).toBeUndefined();
  });

  it('la congestión trae todos los núcleos con los tres niveles', async () => {
    const snapshot = await call<CongestionSnapshot>('GET', '/congestion');

    expect(snapshot.refreshAfterSeconds).toBe(120);
    expect(Date.parse(snapshot.updatedAt)).not.toBeNaN();
    expect(snapshot.cores.map((c) => c.name)).toEqual([
      'Independencia 1',
      'Independencia 2',
      'Lima 1',
      'Lima 2',
      'Lima 3',
    ]);
    expect(new Set(snapshot.cores.map((c) => c.level))).toEqual(new Set(['LOW', 'MEDIUM', 'HIGH']));
    // Umbrales de kpis.md: media desde 60 %, alta desde 85 %.
    for (const { level, occupancyPercent } of snapshot.cores) {
      expect(level).toBe(
        occupancyPercent >= 85 ? 'HIGH' : occupancyPercent >= 60 ? 'MEDIUM' : 'LOW',
      );
    }
  });

  it('los turnos de cursada son los de kpis.md', async () => {
    const shifts = await call<AdminShift[]>('GET', '/admin/shifts');

    expect(shifts.map((s) => [s.id, s.startsAt, s.endsAt])).toEqual([
      ['MORNING', '07:00', '13:00'],
      ['AFTERNOON', '13:00', '18:00'],
      ['NIGHT', '18:00', '22:30'],
    ]);
  });

  const sum = (rows: { reservations: number }[]) =>
    rows.reduce((total, row) => total + row.reservations, 0);

  it('los KPIs cierran entre sí y la espera viene sin datos, como en el backend', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    // 01:00 UTC del 27 = 22:00 del 26 en Buenos Aires.
    vi.setSystemTime(Date.parse('2026-11-27T01:00:00Z'));
    const today = await call<AdminKpis>('GET', '/admin/kpis?period=TODAY');

    expect(today).toMatchObject({
      from: '2026-11-26T03:00:00.000Z',
      to: '2026-11-27T03:00:00.000Z',
      avgWaitSeconds: null,
      wait5To10Percent: null,
      baselinePercent: 43.5,
      capacity: 10,
      previous: { from: '2026-11-25T03:00:00.000Z', to: '2026-11-26T03:00:00.000Z' },
    });
    expect(today.reservations).toBeGreaterThan(0);
    expect(sum(today.reservationsByHour)).toBe(today.reservations);
    expect(sum(today.cores)).toBe(today.reservations);
    expect(today.reservationsByHour.map((h) => h.hour)).toEqual(
      Array.from({ length: 16 }, (_, i) => i + 7),
    );
    expect(new Set(today.cores.map((c) => c.congestion))).toEqual(
      new Set(['LOW', 'MEDIUM', 'HIGH']),
    );
    expect(today.checkInPercent).toBeGreaterThan(0);
    expect(today.avgOccupancy).toBeGreaterThan(0);
    expect(today.checkIns).toBeLessThan(today.reservations);

    const week = await call<AdminKpis>('GET', '/admin/kpis?period=WEEK');
    expect(week.from).toBe('2026-11-20T03:00:00.000Z');
    expect(week.reservations).toBe(today.reservations * 7);
  });

  it('los KPIs se filtran por edificio y turno de cursada', async () => {
    const kpis = await call<AdminKpis>(
      'GET',
      '/admin/kpis?period=MONTH&buildingId=INDEPENDENCIA&shiftId=NIGHT',
    );

    expect(kpis.cores.map((c) => c.coreId)).toEqual(['IND1', 'IND2']);
    expect(kpis.reservationsByHour.map((h) => h.hour)).toEqual([18, 19, 20, 21, 22]);
    expect(kpis.reservations).toBeGreaterThan(0);
    expect(sum(kpis.cores)).toBe(kpis.reservations);
  });

  it('las salidas tienen la forma del contrato y no incluyen ascensores dedicados ajenos', async () => {
    await loginAna();
    const departures = await call<Departure[]>('GET', '/departures?building=independencia');

    expect(departures.length).toBeGreaterThan(0);
    expect(departures[0]).toMatchObject({
      id: expect.any(Number),
      building: 'INDEPENDENCIA',
      capacity: 10,
      remainingSeats: expect.any(Number),
      status: 'SCHEDULED',
    });
    // Ana no tiene prioridad: solo ve el 33 y el 34 de IND2, no los dedicados 05 y 06.
    expect(new Set(departures.map((d) => d.elevator))).toEqual(new Set(['33', '34']));
  });

  it('con núcleo, origen y destino ofrece una salida por hora, la más libre de las que paran en ambos', async () => {
    await loginAna();
    vi.useFakeTimers({ toFake: ['Date'] });
    // La batería baja de L2 (07 y 08) no llega al 8: solo valen el 35 y el 36.
    const offer = await call<Departure[]>('GET', '/departures?core=l2&origin=0&destination=8');
    const eligible = (await call<Departure[]>('GET', '/departures')).filter((d) =>
      ['35', '36'].includes(d.elevator),
    );

    expect(offer.map((d) => d.departureAt)).toEqual([
      ...new Set(eligible.map((d) => d.departureAt)),
    ]);
    for (const d of offer) {
      expect(['35', '36']).toContain(d.elevator);
      expect(d.remainingSeats).toBe(
        Math.max(
          ...eligible.filter((e) => e.departureAt === d.departureAt).map((e) => e.remainingSeats),
        ),
      );
    }
    // Un núcleo solo de dedicados no le ofrece nada a un común.
    expect(await call('GET', '/departures?core=IND1&origin=0&destination=8')).toEqual([]);
  });

  it.each(['core=L2', 'origin=0&destination=8', 'core=L2&origin=0'])(
    'con %s da 400: núcleo, origen y destino van juntos',
    async (query) => {
      await loginAna();

      await expect(call('GET', `/departures?${query}`)).rejects.toMatchObject({ status: 400 });
    },
  );

  it('en la ventana siempre hay una franja completa y otras con lugar, en cada núcleo', async () => {
    await loginAna();
    vi.useFakeTimers({ toFake: ['Date'] });
    const start = Date.now();
    const elevators = await call<Elevator[]>('GET', '/elevators');
    // Sea cual sea la hora a la que se grabe la demo.
    for (let minutes = 0; minutes < 30; minutes += 2) {
      vi.setSystemTime(start + minutes * MIN);
      const departures = await call<Departure[]>('GET', '/departures');
      for (const core of ['L1', 'L2', 'L3', 'IND2']) {
        const codes = elevators.filter((e) => e.core === core).map((e) => e.code);
        const seats = new Map<string, number>();
        for (const d of departures.filter((x) => codes.includes(x.elevator))) {
          seats.set(d.departureAt, Math.max(seats.get(d.departureAt) ?? 0, d.remainingSeats));
        }
        expect(Math.min(...seats.values()), `${core} +${minutes} min`).toBe(0);
        expect(Math.max(...seats.values()), `${core} +${minutes} min`).toBeGreaterThan(0);
      }
    }
  });

  it('el turno inicial no cae en una franja completa', async () => {
    for (let minutes = 0; minutes < 16; minutes += 2) {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(Date.parse('2026-11-27T15:00:00Z') + minutes * MIN);
      vi.resetModules();
      const fresh = await import('./mocks');
      await fresh.mockRequest(
        'POST',
        '/auth/login',
        JSON.stringify({ email: 'ana.perez@uade.edu.ar', password: 'x' }),
        0,
      );
      const active = (await fresh.mockRequest('GET', '/reservations/active', undefined, 0))!
        .data as Reservation;
      expect(active.seatNumber, `+${minutes} min`).toBeLessThanOrEqual(10);
    }
  });

  it('arranca con un turno activo en el 33 con su núcleo y la regla de cancelación', async () => {
    await loginAna();
    const active = await call<Reservation>('GET', '/reservations/active');

    expect(active).toMatchObject({
      id: expect.any(Number),
      status: 'ACTIVE',
      elevator: '33',
      coreName: 'Independencia 2',
      buildingName: 'Independencia',
      cancelCountsAsNoShow: false,
      checkInOutcome: null,
    });
    expect(await call('GET', `/reservations/${active.id}`)).toEqual(active);
    await expect(call('GET', '/reservations/no-existe')).rejects.toMatchObject({ status: 404 });
  });

  it('con un turno activo no se puede reservar otro', async () => {
    await loginAna();
    const departure = await freeDeparture();

    await expect(
      call('POST', '/reservations', {
        departureId: departure.id,
        originFloor: 0,
        destinationFloor: 8,
      }),
    ).rejects.toMatchObject({ status: 409, code: 'ACTIVE_LIMIT_REACHED' });
  });

  it('reservar una salida sin lugar da TURN_FULL', async () => {
    await loginAna();
    await cancelActive();
    const departures = await call<Departure[]>('GET', '/departures?building=LIMA');
    const full = departures.find((d) => d.remainingSeats === 0)!;

    const error = await call('POST', '/reservations', {
      departureId: full.id,
      originFloor: 0,
      destinationFloor: 5,
    }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 409, code: 'TURN_FULL' });
  });

  it('rechaza los pisos con el code del backend', async () => {
    await loginAna();
    await cancelActive();
    const departure = await freeDeparture();
    const tryFloors = (originFloor: number, destinationFloor: number) =>
      call('POST', '/reservations', { departureId: departure.id, originFloor, destinationFloor });

    await expect(tryFloors(0, 0)).rejects.toMatchObject({ code: 'SAME_FLOOR' });
    await expect(tryFloors(0, 12)).rejects.toMatchObject({ code: 'FLOOR_OUT_OF_RANGE' });
    // IND2 no para en el 1.
    await expect(tryFloors(0, 1)).rejects.toMatchObject({ code: 'FLOOR_NOT_SERVED' });
    await expect(tryFloors(0, 3)).rejects.toMatchObject({
      status: 422,
      code: 'LOW_FLOOR_DESTINATION',
      message: 'Para ir a los pisos 1 a 4 usá la escalera.',
    });
  });

  it('un común no puede reservar en un ascensor dedicado', async () => {
    await loginAna();
    await cancelActive();
    const elevators = await call<Elevator[]>('GET', '/elevators');
    const teachers = elevators.findIndex((e) => e.usage === 'TEACHERS');
    const [any] = await call<Departure[]>('GET', '/departures');
    // Misma hora, otro ascensor: el id codifica hora × 100 + índice del ascensor.
    const id = Math.floor(any.id / 100) * 100 + teachers;

    await expect(
      call('POST', '/reservations', { departureId: id, originFloor: 0, destinationFloor: 8 }),
    ).rejects.toMatchObject({ status: 422, code: 'ELEVATOR_NOT_BOOKABLE' });
  });

  it('reservar, consultar y cancelar el turno', async () => {
    await loginAna();
    await cancelActive();
    const free = await freeDeparture();

    const created = await call<Reservation>('POST', '/reservations', {
      departureId: free.id,
      originFloor: 0,
      destinationFloor: 8,
    });
    expect(created).toMatchObject({
      id: expect.any(Number),
      departureId: free.id,
      elevator: free.elevator,
      departureAt: free.departureAt,
      seatNumber: expect.any(Number),
      status: 'ACTIVE',
    });
    expect(await call<Reservation>('GET', '/reservations/active')).toEqual(created);
    const after = (await call<Departure[]>('GET', '/departures?building=INDEPENDENCIA')).find(
      (d) => d.id === free.id,
    )!;
    expect(after.remainingSeats).toBe(free.remainingSeats - 1);

    await call('DELETE', `/reservations/${created.id}`);
    expect(await call('GET', '/reservations/active')).toBeUndefined(); // 204
    expect(await call<Reservation>('GET', `/reservations/${created.id}`)).toMatchObject({
      status: 'CANCELLED',
      seatNumber: null,
    });
  });

  it('edificios y ascensores usan los pisos del relevamiento', async () => {
    const buildings = await call<Building[]>('GET', '/buildings');
    const elevators = await call<Elevator[]>('GET', '/elevators');
    const floorsOf = (code: string) => elevators.find((e) => e.code === code)!.floors;

    expect(buildings.map((b) => [b.code, b.minFloor, b.maxFloor])).toEqual([
      ['LIMA', -4, 10],
      ['INDEPENDENCIA', -4, 11],
    ]);
    expect(floorsOf('33')).not.toContain(1);
    expect(floorsOf('33').at(-1)).toBe(11);
    expect([floorsOf('01')[0], floorsOf('01').at(-1)]).toEqual([-3, 7]);
    // L2: la batería alta no para en el −1 ni en el 1.
    expect(floorsOf('35')).not.toContain(-1);
    expect(floorsOf('07').at(-1)).toBe(5);
    expect(elevators.filter((e) => e.core === 'IND1').map((e) => e.usage)).toEqual([
      'TEACHERS',
      'REDUCED_MOBILITY',
    ]);
  });

  it('login, /users/me y logout simulan la sesión con cookie', async () => {
    await call('POST', '/auth/login', { email: 'admin@uade.edu.ar', password: 'x' });
    expect(await call('GET', '/users/me')).toMatchObject({ role: 'ADMIN' });

    await call('POST', '/auth/logout');
    await expect(call('GET', '/users/me')).rejects.toMatchObject({ status: 401 });
  });

  it('login con cuenta sin verificar da 403 y con contraseña incorrecta 401', async () => {
    const login = (email: string, password: string) =>
      call('POST', '/auth/login', { email, password });

    await expect(login('sin.verificar@uade.edu.ar', 'x')).rejects.toMatchObject({ status: 403 });
    await expect(login('ana.perez@uade.edu.ar', 'incorrecta')).rejects.toMatchObject({
      status: 401,
    });
  });

  it('registro: duplicados dan 409 por campo y la cuenta nueva queda sin verificar', async () => {
    const register = (email: string, legajo: string) =>
      call('POST', '/auth/register', {
        fullName: 'Nueva Persona',
        email,
        legajo,
        password: 'x',
        declaredUserType: 'TEACHER',
      });

    await expect(register('ana.perez@uade.edu.ar', '1099999')).rejects.toMatchObject({
      status: 409,
      violations: [{ field: 'email' }, { field: 'legajo' }],
    });

    await register('nueva@uade.edu.ar', '1234567');
    await expect(
      call('POST', '/auth/login', { email: 'nueva@uade.edu.ar', password: 'x' }),
    ).rejects.toMatchObject({ status: 403 });
  });

  it('check-in: token vencido da 400, después otro ascensor y ya escaneado, con su code', async () => {
    await loginAna();
    await cancelActive();
    const departure = await freeDeparture('elevator=33');
    const turn = await reserveOn(departure);

    await expect(call('POST', '/check-ins', { token: 'VENCIDO-1' })).rejects.toMatchObject({
      status: 400,
      code: 'INVALID_QR_TOKEN',
    });
    // Fuera de la ventana (−1 min a +2 min) el token del kiosco no sirve.
    await expect(call('POST', '/check-ins', { token: 'mock-qr-34-1' })).rejects.toMatchObject({
      status: 409,
      code: 'OUTSIDE_CHECK_IN_WINDOW',
    });
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(Date.parse(departure.departureAt) + 30_000);
    const result = await call<CheckIn>('POST', '/check-ins', { token: 'mock-qr-34-1' });
    expect(result).toMatchObject({
      reservationId: turn.id,
      outcome: 'OTHER_ELEVATOR',
      reservationStatus: 'USED',
      elevator: '34',
      reservedElevator: '33',
      departureAt: departure.departureAt,
    });
    await expect(call('POST', '/check-ins', { token: 'X' })).rejects.toMatchObject({
      status: 409,
      code: 'ALREADY_CHECKED_IN',
    });
  });

  it('historial paginado y faltas recientes con los parámetros de la regla', async () => {
    await call('POST', '/auth/login', { email: 'ana.perez@uade.edu.ar', password: 'x' });

    const first = await call<ReservationPage>('GET', '/reservations/history');
    const second = await call<ReservationPage>(
      'GET',
      `/reservations/history?cursor=${first.nextCursor}`,
    );
    expect(first.items).toHaveLength(10);
    expect(second.items[0].id).not.toBe(first.items[0].id);
    expect(first.items.map((t) => t.departureAt)).toEqual(
      first.items
        .map((t) => t.departureAt)
        .sort()
        .reverse(),
    );
    const results = new Set(first.items.map((t) => `${t.status} ${t.checkInOutcome}`));
    expect(results.size).toBeGreaterThanOrEqual(6);

    const noShows = await call<NoShowStatus>('GET', '/me/no-shows');
    expect(noShows).toMatchObject({ threshold: 3, windowDays: 7, exempt: false });
    expect(noShows.recentNoShows).toBeGreaterThan(0);
    await call('POST', '/auth/logout');
  });

  it('los links del mail inválidos, usados o vencidos traen el code del backend', async () => {
    for (const [token, code] of [
      ['VENCIDO', 'TOKEN_EXPIRED'],
      ['USADO', 'TOKEN_USED'],
      ['', 'TOKEN_INVALID'],
    ]) {
      await expect(call('POST', '/auth/verify', { token })).rejects.toMatchObject({
        status: 400,
        code,
      });
    }
    await expect(
      call('POST', '/auth/password/reset', { token: 'VENCIDO', newPassword: 'x' }),
    ).rejects.toMatchObject({ status: 400, code: 'TOKEN_EXPIRED' });
  });

  it('las preferencias de notificaciones se guardan', async () => {
    const prefs = await call<NotificationPreferences>('GET', '/me/notification-preferences');
    await call('PUT', '/me/notification-preferences', { ...prefs, departureReminder: false });

    expect(await call('GET', '/me/notification-preferences')).toMatchObject({
      departureReminder: false,
    });
  });

  it('kioscos: el alta da el secreto una vez y el QR sale solo con la credencial', async () => {
    const created = await call<CreatedKiosk>('POST', '/admin/kiosks', {
      elevatorId: '33',
      simulated: true,
    });
    expect(created).toMatchObject({
      elevatorId: '33',
      simulated: true,
      secret: expect.any(String),
    });

    const list = await call<KioskList>('GET', '/admin/kiosks');
    expect(list.simulatorEnabled).toBe(true);
    expect(list.kiosks.find((k) => k.id === created.id)).not.toHaveProperty('secret');

    const qr = await call<QrToken>('GET', '/kiosks/me/qr-token', undefined, {
      Authorization: `Bearer ${created.secret}`,
    });
    expect(qr.elevatorId).toBe('33');
    await expect(
      call('GET', '/kiosks/me/qr-token', undefined, { Authorization: 'Bearer cualquiera' }),
    ).rejects.toMatchObject({ status: 401 });
    await expect(call('POST', '/admin/kiosks', { elevatorId: '99' })).rejects.toMatchObject({
      status: 404,
    });
  });

  // Cortes de ADR 0026: 07, 13, 18 y 23 h de Buenos Aires.
  it.each([
    ['2026-11-26T12:00:00Z', '2026-11-26T16:00:00.000Z'],
    ['2026-11-26T16:00:00Z', '2026-11-26T21:00:00.000Z'],
    ['2026-11-27T01:30:00Z', '2026-11-27T02:00:00.000Z'],
    ['2026-11-27T02:30:00Z', '2026-11-27T10:00:00.000Z'],
  ])('el QR del kiosco pedido a las %s vence en el próximo cambio de turno', async (now, next) => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(now) });
    const headers = { Authorization: 'Bearer mock-33-x' };

    const qr = await call<QrToken>('GET', '/kiosks/me/qr-token', undefined, headers);
    expect(qr.expiresAt).toBe(next);
    // En el mismo turno el kiosco recibe siempre el mismo código.
    vi.setSystemTime(Date.parse(now) + MIN);
    expect(await call('GET', '/kiosks/me/qr-token', undefined, headers)).toEqual(qr);
  });

  it('ascensor fuera de servicio: cancela sin falta, deja de ofrecerse y se reactiva', async () => {
    await loginAna();
    const before = await call<AdminElevator[]>('GET', '/admin/elevators');
    const count = before.find((e) => e.code === '33')!.upcomingReservations;

    const [slot] = await call<Departure[]>('GET', '/departures?elevator=33');

    const out = await call<ElevatorServiceResult>('POST', '/admin/elevators/33/out-of-service');
    expect(out).toEqual({ code: '33', active: false, cancelledReservations: count });
    const after = await call<AdminElevator[]>('GET', '/admin/elevators');
    expect(after.find((e) => e.code === '33')).toMatchObject({
      active: false,
      upcomingReservations: 0,
    });
    const listed = await call<Elevator[]>('GET', '/elevators');
    expect(listed.find((e) => e.code === '33')?.active).toBe(false);
    expect(listed.find((e) => e.code === '34')?.active).toBe(true);
    expect((await call<Departure[]>('GET', '/departures?elevator=33')).length).toBe(0);
    await expect(
      call('POST', '/reservations', { departureId: slot.id, originFloor: 0, destinationFloor: 8 }),
    ).rejects.toMatchObject({ status: 422, code: 'ELEVATOR_NOT_BOOKABLE' });
    await expect(call('POST', '/admin/elevators/99/out-of-service')).rejects.toMatchObject({
      status: 404,
    });

    await call('POST', '/admin/elevators/33/in-service');
    expect((await call<Elevator[]>('GET', '/elevators')).find((e) => e.code === '33')?.active).toBe(
      true,
    );
  });

  it('todas las rutas que usa la app tienen mock: ninguna llega al proxy (502)', async () => {
    await loginAna();
    // Las de services/api.ts menos /ping.
    const routes = `POST /auth/login, POST /auth/refresh, POST /auth/logout, POST /auth/register,
      POST /auth/verify, POST /auth/resend-verification, POST /auth/password/forgot,
      POST /auth/password/reset, GET /users/me, GET /buildings, GET /elevators,
      GET /departures?building=LIMA, GET /congestion, GET /reservations/active,
      GET /reservations/history, GET /reservations/1, POST /reservations, DELETE /reservations/1,
      POST /check-ins, GET /me/no-shows, GET /me/notification-preferences,
      PUT /me/notification-preferences, GET /admin/kpis?period=TODAY, GET /admin/shifts,
      GET /admin/teachers/pending, POST /admin/teachers/x/approve, POST /admin/teachers/x/reject,
      GET /admin/users?query=ana, PUT /admin/users/x/role, PUT /admin/users/x/declared-type,
      GET /admin/priority-users, DELETE /admin/priority-users/x, GET /admin/elevators,
      POST /admin/elevators/x/out-of-service, POST /admin/elevators/x/in-service,
      GET /admin/kiosks, POST /admin/kiosks, POST /admin/kiosks/x/revoke, GET /kiosks/me/qr-token`;
    for (const route of routes.split(',')) {
      const [method, path] = route.trim().split(' ');
      // Con un cuerpo vacío el handler puede fallar: también prueba que la ruta tiene mock.
      const result = await mockRequest(method, path, '{}', 0).catch((e: unknown) => e);
      expect(result, route).toBeDefined();
    }
  });

  it('otra pestaña recupera la sesión, como con la cookie del refresh', async () => {
    await loginAna();
    sessionStorage.clear(); // pestaña nueva: comparte localStorage, no sessionStorage

    await call('POST', '/auth/refresh');
    expect(await call('GET', '/users/me')).toMatchObject({ email: 'ana.perez@uade.edu.ar' });

    await call('POST', '/auth/logout');
    sessionStorage.clear();
    await expect(call('POST', '/auth/refresh')).rejects.toMatchObject({ status: 401 });
  });

  it.each([
    ['', 'ON_TIME', 'USED'],
    ['&checkin=otro', 'OTHER_ELEVATOR', 'USED'],
    ['&checkin=tarde', 'LATE', 'NO_SHOW'],
  ])('el botón de demo con ?demo=1%s da %s', async (scenario, outcome, reservationStatus) => {
    await loginAna();
    await cancelActive();
    await reserveOn(await freeDeparture());
    window.history.replaceState(null, '', `/check-in?demo=1${scenario}`);
    try {
      expect(await call<CheckIn>('POST', '/check-ins', { token: 'DEMO' })).toMatchObject({
        outcome,
        reservationStatus,
      });
    } finally {
      window.history.replaceState(null, '', '/');
    }
  });
});
