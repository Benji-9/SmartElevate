import { vi } from 'vitest';
import type { Reservation, User } from '../types/api';

type Reply = { status?: number; body?: unknown; headers?: Record<string, string> };
type Routes = Record<string, Reply | ((init?: RequestInit) => Reply)>;

/**
 * Stubea `fetch` respondiendo por `"MÉTODO /ruta"` (sin el prefijo `/api`).
 * Lo que no está en `routes` responde 404.
 */
export function stubApi(routes: Routes) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const route = routes[`${init?.method ?? 'GET'} ${url.replace(/^\/api/, '')}`];
    const {
      status = 200,
      body,
      headers,
    } = (typeof route === 'function' ? route(init) : route) ?? {
      status: 404,
      body: { message: 'Not Found' },
    };
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json', ...headers },
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export const testUser: User = {
  id: 'u-1',
  email: 'ana.perez@uade.edu.ar',
  fullName: 'Ana Pérez',
  legajo: '1099999',
  role: 'USER',
  priority: 'NONE',
  declaredUserType: 'STUDENT',
};

/** Reserva activa como la manda el backend. 17:04 UTC = 14:04 en Buenos Aires. */
export const testReservation: Reservation = {
  id: 55,
  departureId: 7,
  elevator: '33',
  coreName: 'Lima 1',
  buildingName: 'Lima',
  departureAt: '2026-09-29T17:04:00Z',
  cancelFreeUntil: '2026-09-29T16:54:00Z',
  seatNumber: 3,
  originFloor: 0,
  destinationFloor: 5,
  status: 'ACTIVE',
  createdAt: '2026-09-29T16:50:00Z',
  cancelCountsAsNoShow: false,
  checkInOutcome: null,
};

/** Respuesta de login/refresh del backend (`TokenResponse`). */
export const tokens = (n = 1) => ({
  accessToken: `token-${n}`,
  tokenType: 'Bearer',
  expiresIn: 900,
});

/** Rutas para arrancar con sesión (la cookie del refresh la simula el stub de `/auth/refresh`). */
export const signedIn = (user: User = testUser): Routes => ({
  'POST /auth/refresh': { body: tokens() },
  'GET /users/me': { body: user },
  'POST /auth/logout': { status: 204 },
  'GET /ping': { body: { status: 'ok' } },
});

/** Rutas para arrancar sin sesión. */
export const signedOut: Routes = {
  'POST /auth/refresh': { status: 401, body: { message: 'Sin sesión' } },
  'GET /ping': { body: { status: 'ok' } },
};
