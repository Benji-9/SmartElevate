import { describe, expect, it } from 'vitest';
import type { Departure, Reservation } from '../types/pending';
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

  it('reservar una salida llena da 409', async () => {
    const departures = await call<Departure[]>('GET', '/cores/L1/departures');
    const full = departures.find((d) => d.occupied >= d.capacity)!;

    const error = await call('POST', '/reservations', {
      departureId: full.id,
      originFloor: 0,
      destinationFloor: 5,
    }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
  });

  it('reservar, consultar y cancelar el turno activo', async () => {
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
});
