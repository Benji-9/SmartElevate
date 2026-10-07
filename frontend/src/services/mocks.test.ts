import { describe, expect, it } from 'vitest';
import type {
  AdminKpis,
  AdminShift,
  CheckInResult,
  CongestionSnapshot,
  Core,
  Departure,
  FloorOption,
  NoShowStatus,
  NotificationPreferences,
  PriorityUploadRules,
  Reservation,
  TripPage,
} from '../types/pending';
import type { Building } from '../types/api';
import { ApiError } from './api';
import { mockRequest } from './mocks';

const call = async <T>(method: string, path: string, body?: unknown) =>
  (await mockRequest(method, path, body === undefined ? undefined : JSON.stringify(body), 0))
    ?.data as T;

describe('mocks', () => {
  it('deja pasar al backend lo que no tiene mock', async () => {
    expect(await mockRequest('GET', '/ping', undefined, 0)).toBeUndefined();
  });

  it('las salidas traen cupo y duración como parámetros de la API', async () => {
    const departures = await call<Departure[]>('GET', '/cores/L1/departures');

    expect(departures.length).toBeGreaterThan(0);
    expect(departures[0]).toMatchObject({ coreId: 'L1', capacity: 10, durationMinutes: 2 });
  });

  it('arranca con un turno activo con su núcleo y la regla de cancelación', async () => {
    const active = await call<Reservation>('GET', '/reservations/active');

    expect(active).toMatchObject({
      status: 'ACTIVE',
      core: { id: 'L2', buildingName: 'Lima', hall: expect.any(String) },
      cancelCountsAsNoShow: false,
    });
    expect(await call('GET', `/reservations/${active.id}`)).toEqual(active);
    await expect(call('GET', '/reservations/no-existe')).rejects.toMatchObject({ status: 404 });
  });

  it('con un turno activo no se puede reservar otro', async () => {
    const [departure] = await call<Departure[]>('GET', '/cores/IND2/departures');

    await expect(
      call('POST', '/reservations', {
        departureId: departure.id,
        originFloor: 0,
        destinationFloor: 8,
      }),
    ).rejects.toMatchObject({ status: 409, message: expect.stringContaining('turno activo') });
  });

  it('reservar una salida llena da 409', async () => {
    const active = await call<Reservation>('GET', '/reservations/active');
    if (active) await call('DELETE', `/reservations/${active.id}`);
    const departures = await call<Departure[]>('GET', '/cores/L1/departures');
    const full = departures.find((d) => d.occupied >= d.capacity)!;

    const error = await call('POST', '/reservations', {
      departureId: full.id,
      originFloor: 0,
      destinationFloor: 5,
    }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
    expect((error as ApiError).message).toContain('llena');
  });

  it('reservar, consultar y cancelar el turno activo', async () => {
    const active = await call<Reservation>('GET', '/reservations/active');
    if (active) await call('DELETE', `/reservations/${active.id}`);
    const departures = await call<Departure[]>('GET', '/cores/IND2/departures');
    const free = departures.find((d) => d.occupied < d.capacity)!;

    const created = await call<Reservation>('POST', '/reservations', {
      departureId: free.id,
      originFloor: 0,
      destinationFloor: 8,
    });
    expect(await call<Reservation>('GET', '/reservations/active')).toEqual(created);

    await call('DELETE', `/reservations/${created.id}`);
    expect(await call('GET', '/reservations/active')).toBeNull();
  });

  it('la congestión trae cada cuánto refrescar', async () => {
    const snapshot = await call<CongestionSnapshot>('GET', '/congestion');

    expect(snapshot.refreshAfterSeconds).toBeGreaterThan(0);
    expect(snapshot.cores.map((c) => c.name)).toContain('Independencia 2');
  });

  it('los pisos de destino marcan el origen y los pisos bajos (1 a 4) como no elegibles', async () => {
    await call('POST', '/auth/login', { email: 'ana.perez@uade.edu.ar', password: 'x' });

    const floors = await call<FloorOption[]>('GET', '/cores/L1/floors?origin=0');
    const byFloor = (n: number) => floors.find((f) => f.floor === n);

    expect(byFloor(0)).toMatchObject({ eligible: false });
    expect(byFloor(1)).toMatchObject({ eligible: false, reason: expect.any(String) });
    expect(byFloor(4)).toMatchObject({ eligible: false, reason: expect.any(String) });
    expect(byFloor(-1)).toEqual({ floor: -1, eligible: true, reason: null });
    expect(byFloor(5)).toEqual({ floor: 5, eligible: true, reason: null });
    await call('POST', '/auth/logout');
  });

  it('edificios y núcleos usan los pisos del relevamiento', async () => {
    const buildings = await call<Building[]>('GET', '/buildings');
    const cores = await call<Core[]>('GET', '/cores');
    const floorsOf = (id: string) => cores.find((c) => c.id === id)!.floors;

    expect(buildings.map((b) => [b.code, b.minFloor, b.maxFloor])).toEqual([
      ['LIMA', -4, 10],
      ['INDEPENDENCIA', -4, 11],
    ]);
    expect(floorsOf('IND2')).not.toContain(1);
    expect(floorsOf('IND2').at(-1)).toBe(11);
    expect([floorsOf('L1')[0], floorsOf('L1').at(-1)]).toEqual([-3, 7]);
  });

  it('login, /me y logout simulan la sesión con cookie', async () => {
    await call('POST', '/auth/login', { email: 'admin@uade.edu.ar', password: 'x' });
    expect(await call('GET', '/me')).toMatchObject({ role: 'ADMIN' });

    await call('POST', '/auth/logout');
    await expect(call('GET', '/me')).rejects.toMatchObject({ status: 401 });
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

  it('check-in: código vencido da 400, después cumplido, ya escaneado y encuesta única', async () => {
    const active = await call<Reservation>('GET', '/reservations/active');
    if (active) await call('DELETE', `/reservations/${active.id}`);
    const [departure] = await call<Departure[]>('GET', '/cores/IND2/departures');
    const turn = await call<Reservation>('POST', '/reservations', {
      departureId: departure.id,
      originFloor: 0,
      destinationFloor: 8,
    });

    await expect(call('POST', '/check-ins', { code: 'VENCIDO-1' })).rejects.toMatchObject({
      status: 400,
    });
    const result = await call<CheckInResult>('POST', '/check-ins', { code: 'OTRO-42' });
    expect(result).toMatchObject({
      reservationId: turn.id,
      outcome: 'OTHER_ELEVATOR',
      coreName: 'Independencia 2',
      departsAt: departure.departsAt,
    });
    await expect(call('POST', '/check-ins', { code: 'X' })).rejects.toMatchObject({
      status: 409,
      message: expect.stringContaining('Ya registraste'),
    });

    const feedback = `/check-ins/${turn.id}/wait-feedback`;
    await call('POST', feedback, { range: 'FROM_5_TO_10' });
    await expect(call('POST', feedback, { range: 'UNDER_2' })).rejects.toMatchObject({
      status: 409,
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

    const first = await call<TripPage>('GET', '/reservations/history');
    const second = await call<TripPage>('GET', `/reservations/history?cursor=${first.nextCursor}`);
    expect(first.items).toHaveLength(10);
    expect(second.items[0].id).not.toBe(first.items[0].id);
    expect(new Set(first.items.map((t) => t.result)).size).toBe(5);

    const noShows = await call<NoShowStatus>('GET', '/me/no-shows');
    expect(noShows).toMatchObject({ threshold: 3, windowDays: 7, exempt: false });
    expect(noShows.recentNoShows).toBeGreaterThan(0);
    await call('POST', '/auth/logout');
  });

  it('las preferencias de notificaciones se guardan', async () => {
    const prefs = await call<NotificationPreferences>('GET', '/me/notification-preferences');
    await call('PUT', '/me/notification-preferences', { ...prefs, departureReminder: true });

    expect(await call('GET', '/me/notification-preferences')).toMatchObject({
      departureReminder: true,
    });
  });

  it('KPIs del panel admin: filtran por sede y escalan con el período', async () => {
    const today = await call<AdminKpis>('GET', '/admin/kpis?period=TODAY');
    const week = await call<AdminKpis>('GET', '/admin/kpis?period=WEEK');
    const lima = await call<AdminKpis>('GET', '/admin/kpis?period=TODAY&buildingId=LIMA');

    expect(today).toMatchObject({ baselinePercent: 43.5, capacity: 10 });
    expect(Date.parse(today.to) - Date.parse(today.from)).toBe(86_400_000);
    expect(today.reservations).toBe(
      today.reservationsByHour.reduce((sum, h) => sum + h.reservations, 0),
    );
    expect(week.reservations).toBeGreaterThan(today.reservations);
    expect(lima.cores.map((c) => c.coreId)).toEqual(['L1', 'L2', 'L3']);
    await expect(call('GET', '/admin/kpis?period=YEAR')).rejects.toMatchObject({ status: 400 });
  });

  it('KPIs del panel admin: el turno de cursada recorta las reservas a su horario', async () => {
    const shifts = await call<AdminShift[]>('GET', '/admin/shifts');
    expect(shifts).toEqual([
      { id: 'MORNING', name: 'Turno mañana', startsAt: '07:00', endsAt: '12:15' },
    ]);

    const allDay = await call<AdminKpis>('GET', '/admin/kpis?period=TODAY');
    const morning = await call<AdminKpis>('GET', '/admin/kpis?period=TODAY&shiftId=MORNING');

    expect(morning.reservationsByHour.map((h) => h.hour)).toEqual([7, 8, 9, 10, 11, 12]);
    expect(morning.reservations).toBe(
      morning.reservationsByHour.reduce((sum, h) => sum + h.reservations, 0),
    );
    expect(morning.reservations).toBeLessThan(allDay.reservations);
    await expect(call('GET', '/admin/kpis?period=TODAY&shiftId=NIGHT')).rejects.toMatchObject({
      status: 404,
    });
  });
});
