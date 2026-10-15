import { describe, expect, it } from 'vitest';
import type { PriorityUploadRules } from '../types/pending';
import type {
  AdminElevator,
  Building,
  CheckIn,
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
const freeDeparture = async (building = 'INDEPENDENCIA') =>
  (await call<Departure[]>('GET', `/departures?building=${building}`)).find(
    (d) => d.remainingSeats > 0,
  )!;

describe('mocks', () => {
  it('deja pasar al backend lo que no tiene mock', async () => {
    expect(await mockRequest('GET', '/ping', undefined, 0)).toBeUndefined();
    // Congestión y KPIs ya los calcula el backend (#220).
    for (const path of ['/congestion', '/admin/kpis?period=TODAY', '/admin/shifts']) {
      expect(await mockRequest('GET', path, undefined, 0)).toBeUndefined();
    }
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
    const departure = (await call<Departure[]>('GET', '/departures?elevator=33')).find(
      (d) => d.remainingSeats > 0,
    )!;
    const turn = await call<Reservation>('POST', '/reservations', {
      departureId: departure.id,
      originFloor: 0,
      destinationFloor: 8,
    });

    await expect(call('POST', '/check-ins', { token: 'VENCIDO-1' })).rejects.toMatchObject({
      status: 400,
      code: 'INVALID_QR_TOKEN',
    });
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

  it('certificado: pide consentimiento, valida tipo y tamaño y queda pendiente', async () => {
    const rules = await call<PriorityUploadRules>('GET', '/priority-requests/upload-rules');
    expect(rules.acceptedTypes).toContain('application/pdf');
    expect(await call('GET', '/priority-requests/me')).toBeNull();

    const submit = (file: File, consent = 'true') => {
      const form = new FormData();
      form.append('consentAccepted', consent);
      form.append('certificate', file);
      return mockRequest('POST', '/priority-requests', form, 0);
    };
    const pdf = new File(['%PDF'], 'c.pdf', { type: 'application/pdf' });

    await expect(submit(pdf, 'false')).rejects.toMatchObject({ status: 400 });
    await expect(submit(new File(['x'], 'c.txt', { type: 'text/plain' }))).rejects.toMatchObject({
      status: 415,
    });
    const big = new File([new Uint8Array(rules.maxSizeBytes + 1)], 'c.png', {
      type: 'image/png',
    });
    await expect(submit(big)).rejects.toMatchObject({ status: 413 });

    await submit(pdf);
    expect(await call('GET', '/priority-requests/me')).toMatchObject({ status: 'PENDING' });
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

  it('las preferencias de notificaciones se guardan', async () => {
    const prefs = await call<NotificationPreferences>('GET', '/me/notification-preferences');
    await call('PUT', '/me/notification-preferences', { ...prefs, departureReminder: false });

    expect(await call('GET', '/me/notification-preferences')).toMatchObject({
      departureReminder: false,
    });
  });

  it('kioscos: el alta da el secreto una vez y el QR vence en ciclos de 30 s', async () => {
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
    expect(Date.parse(qr.expiresAt) % 30_000).toBe(0);
    expect(qr.elevatorId).toBe('33');
    await expect(
      call('GET', '/kiosks/me/qr-token', undefined, { Authorization: 'Bearer cualquiera' }),
    ).rejects.toMatchObject({ status: 401 });
    await expect(call('POST', '/admin/kiosks', { elevatorId: '99' })).rejects.toMatchObject({
      status: 404,
    });
  });

  it('ascensor fuera de servicio: cancela sin falta, deja de ofrecerse y se reactiva', async () => {
    await loginAna();
    const before = await call<AdminElevator[]>('GET', '/admin/elevators');
    const count = before.find((e) => e.code === '33')!.upcomingReservations;

    const out = await call<ElevatorServiceResult>('POST', '/admin/elevators/33/out-of-service');
    expect(out).toEqual({ code: '33', active: false, cancelledReservations: count });
    const after = await call<AdminElevator[]>('GET', '/admin/elevators');
    expect(after.find((e) => e.code === '33')).toMatchObject({
      active: false,
      upcomingReservations: 0,
    });
    expect((await call<Elevator[]>('GET', '/elevators')).some((e) => e.code === '33')).toBe(false);
    expect((await call<Departure[]>('GET', '/departures?elevator=33')).length).toBe(0);
    await expect(call('POST', '/admin/elevators/99/out-of-service')).rejects.toMatchObject({
      status: 404,
    });

    await call('POST', '/admin/elevators/33/in-service');
    expect((await call<Elevator[]>('GET', '/elevators')).some((e) => e.code === '33')).toBe(true);
  });
});
