import { describe, expect, it } from 'vitest';
import type { CongestionSnapshot, Departure, FloorOption, Reservation } from '../types/pending';
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

  it('los pisos de destino marcan el origen y los trayectos cortos como no elegibles', async () => {
    await call('POST', '/auth/login', { email: 'ana.perez@uade.edu.ar', password: 'x' });

    const floors = await call<FloorOption[]>('GET', '/cores/L1/floors?origin=3');
    const byFloor = (n: number) => floors.find((f) => f.floor === n);

    expect(byFloor(3)).toMatchObject({ eligible: false });
    expect(byFloor(4)).toMatchObject({ eligible: false, reason: expect.any(String) });
    expect(byFloor(8)).toEqual({ floor: 8, eligible: true, reason: null });
    await call('POST', '/auth/logout');
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
});
