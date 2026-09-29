import { vi } from 'vitest';
import type { User } from '../types/pending';

type Reply = { status?: number; body?: unknown };
type Routes = Record<string, Reply | ((init?: RequestInit) => Reply)>;

/**
 * Stubea `fetch` respondiendo por `"MÉTODO /ruta"` (sin el prefijo `/api`).
 * Lo que no está en `routes` responde 404.
 */
export function stubApi(routes: Routes) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const route = routes[`${init?.method ?? 'GET'} ${url.replace(/^\/api/, '')}`];
    const { status = 200, body } = (typeof route === 'function' ? route(init) : route) ?? {
      status: 404,
      body: { message: 'Not Found' },
    };
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
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

/** Rutas para arrancar con sesión (cookie de refresh válida). */
export const signedIn = (user: User = testUser): Routes => ({
  'POST /auth/refresh': { body: { accessToken: 'token-1' } },
  'GET /me': { body: user },
  'POST /auth/logout': { status: 204 },
  'GET /ping': { body: { status: 'ok' } },
});

/** Rutas para arrancar sin sesión. */
export const signedOut: Routes = {
  'POST /auth/refresh': { status: 401, body: { message: 'Sin sesión' } },
  'GET /ping': { body: { status: 'ok' } },
};
