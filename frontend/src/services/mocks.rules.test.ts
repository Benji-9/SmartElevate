import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CheckIn, Departure, NoShowStatus, Reservation } from '../types/api';

const MIN = 60_000;

afterEach(() => {
  vi.useRealTimers();
});

describe('mocks: reglas del servidor con estado propio', () => {
  /** Módulo nuevo por test: las faltas, el tope diario y las reservas no se arrastran. */
  async function fresh() {
    vi.resetModules();
    const { mockRequest } = await import('./mocks');
    const send = async <T>(method: string, path: string, body?: unknown) =>
      (await mockRequest(method, path, body === undefined ? undefined : JSON.stringify(body), 0))
        ?.data as T;
    await send('POST', '/auth/login', { email: 'ana.perez@uade.edu.ar', password: 'x' });
    const departures = (query: string) => send<Departure[]>('GET', `/departures?${query}`);
    /** Con lugar y a más de 10 min: cancelarla todavía es sin falta. */
    const far = async (query = 'building=INDEPENDENCIA') =>
      (await departures(query)).find(
        (d) => d.remainingSeats > 0 && Date.parse(d.departureAt) - Date.now() > 11 * MIN,
      )!;
    const reserve = (d: Departure) =>
      send<Reservation>('POST', '/reservations', {
        departureId: d.id,
        originFloor: 0,
        destinationFloor: 8,
      });
    const cancelFirst = async () => {
      const active = await send<Reservation | undefined>('GET', '/reservations/active');
      if (active) await send('DELETE', `/reservations/${active.id}`);
    };
    return { send, departures, far, reserve, cancelFirst };
  }

  it('cancelar a más de 10 min es sin falta; después, tardía, cuenta como falta y suspende 24 h', async () => {
    const { send, departures, far, reserve, cancelFirst } = await fresh();
    await cancelFirst(); // el turno de ejemplo está a ~16 min: CANCELLED
    const onTime = await reserve(await far());
    expect(onTime.cancelCountsAsNoShow).toBe(false);
    expect(Date.parse(onTime.departureAt) - Date.parse(onTime.cancelFreeUntil)).toBe(10 * MIN);
    await send('DELETE', `/reservations/${onTime.id}`);
    expect(await send<Reservation>('GET', `/reservations/${onTime.id}`)).toMatchObject({
      status: 'CANCELLED',
    });

    const soon = (await departures('building=INDEPENDENCIA')).find((d) => d.remainingSeats > 0)!;
    const late = await reserve(soon);
    expect(late.cancelCountsAsNoShow).toBe(true);
    await send('DELETE', `/reservations/${late.id}`);
    expect(await send<Reservation>('GET', `/reservations/${late.id}`)).toMatchObject({
      status: 'CANCELLED_LATE',
    });

    // 2 faltas de ejemplo + esta = umbral: suspendido.
    const faults = await send<NoShowStatus>('GET', '/me/no-shows');
    expect(faults.recentNoShows).toBeGreaterThanOrEqual(3); // suspendido: las de 7 días
    expect(faults.suspendedUntil).not.toBeNull();
    await expect(reserve(await far())).rejects.toMatchObject({
      status: 409,
      code: 'NO_SHOW_SUSPENDED',
    });
  });

  it('no se cancela una reserva usada ni una cuya salida ya pasó', async () => {
    const { send, far, reserve, cancelFirst } = await fresh();
    const seeded = await send<Reservation>('GET', '/reservations/active');
    await cancelFirst();
    const used = await reserve(await far());
    await send('POST', '/check-ins', { token: 'DEMO' });
    await expect(send('DELETE', `/reservations/${used.id}`)).rejects.toMatchObject({
      status: 409,
      code: 'ALREADY_CHECKED_IN',
    });

    const next = await reserve(await far());
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(Date.parse(next.departureAt) + 1);
    await expect(send('DELETE', `/reservations/${next.id}`)).rejects.toMatchObject({
      status: 409,
      code: 'DEPARTURE_ALREADY_PASSED',
    });
    // Cancelar una ya cancelada es idempotente.
    await expect(send('DELETE', `/reservations/${seeded.id}`)).resolves.toBeUndefined();
  });

  it('el tope diario es de 8 y solo lo devuelve una cancelación sin falta', async () => {
    const { send, far, reserve, cancelFirst } = await fresh();
    await cancelFirst(); // sin falta: no cuenta
    for (let i = 0; i < 8; i++) {
      await reserve(await far());
      await send('POST', '/check-ins', { token: 'DEMO' }); // USED: sigue contando
    }
    await expect(reserve(await far())).rejects.toMatchObject({
      status: 409,
      code: 'DAILY_LIMIT_REACHED',
    });
  });

  it('los 2 lugares de prioritarios se liberan 3 min antes de la salida', async () => {
    const { departures, far } = await fresh();
    const target = await far();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(Date.parse(target.departureAt) - 2.5 * MIN);
    const after = (await departures('building=INDEPENDENCIA')).find((d) => d.id === target.id)!;

    expect(after.remainingSeats).toBe(target.remainingSeats + 2);
  });

  it('un ascensor fuera de servicio no se puede reservar: ELEVATOR_NOT_BOOKABLE', async () => {
    const { send, far, reserve, cancelFirst } = await fresh();
    await cancelFirst();
    const target = await far('elevator=34');
    await send('POST', '/admin/elevators/34/out-of-service');

    await expect(reserve(target)).rejects.toMatchObject({
      status: 422,
      code: 'ELEVATOR_NOT_BOOKABLE',
    });
  });

  it('check-in: rechaza fuera de −1 min a +2 min y a tiempo hasta +60 s', async () => {
    const { send, far, reserve, cancelFirst } = await fresh();
    await cancelFirst();
    const turn = await reserve(await far('elevator=33'));
    const departure = Date.parse(turn.departureAt);
    const at = (ms: number) => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(departure + ms);
    };
    const scan = () => send<CheckIn>('POST', '/check-ins', { token: 'mock-qr-33-1' });

    at(-MIN - 1);
    await expect(scan()).rejects.toMatchObject({ code: 'OUTSIDE_CHECK_IN_WINDOW' });
    at(2 * MIN + 1);
    await expect(scan()).rejects.toMatchObject({ code: 'OUTSIDE_CHECK_IN_WINDOW' });
    at(MIN);
    await expect(scan()).resolves.toMatchObject({ outcome: 'ON_TIME', reservationStatus: 'USED' });
  });

  it('check-in fuera de hora (+60 s a +2 min) deja la reserva en no-show', async () => {
    const { send, far, reserve, cancelFirst } = await fresh();
    await cancelFirst();
    const turn = await reserve(await far('elevator=33'));
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(Date.parse(turn.departureAt) + MIN + 1);

    await expect(
      send<CheckIn>('POST', '/check-ins', { token: 'mock-qr-33-1' }),
    ).resolves.toMatchObject({ outcome: 'LATE', reservationStatus: 'NO_SHOW' });
    expect(await send<Reservation>('GET', `/reservations/${turn.id}`)).toMatchObject({
      status: 'NO_SHOW',
      checkInOutcome: 'LATE',
    });
  });

  it('sin turno activo ni check-in previo da NO_ACTIVE_RESERVATION', async () => {
    const { send, cancelFirst } = await fresh();
    await cancelFirst();

    await expect(send('POST', '/check-ins', { token: 'X' })).rejects.toMatchObject({
      status: 409,
      code: 'NO_ACTIVE_RESERVATION',
    });
  });
});
