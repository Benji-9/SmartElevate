import { vi } from 'vitest';
import type { User } from '../types/pending';

type Reply = { status?: number; body?: unknown };
type Routes = Record<string, Reply | ((init?: RequestInit) => Reply)>;

/**
 * Stubea `fetch` respondiendo por `"MÉTODO /ruta"` (sin el prefijo `/api`).
 * Lo que no está en `routes` responde 404.
 */
export function stubApi(routes: Routes) {
  // Si el stub atiende /auth/refresh, simula una pestaña con un refresh token ya guardado.
  try {
    if (routes['POST /auth/refresh'] && !sessionStorage.getItem('smartelevate.refresh')) {
      sessionStorage.setItem('smartelevate.refresh', 'refresh-0');
    }
  } catch {
    // Un test que bloquea el storage a propósito.
  }
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

/** Respuesta de login/refresh del backend (`TokenResponse`). */
export const tokens = (n = 1) => ({
  accessToken: `token-${n}`,
  refreshToken: `refresh-${n}`,
  tokenType: 'Bearer',
  expiresIn: 900,
});

/** Rutas para arrancar con sesión (refresh válido; `stubApi` deja el refresh token guardado). */
export const signedIn = (user: User = testUser): Routes => ({
  'POST /auth/refresh': { body: tokens() },
  'GET /me': { body: user },
  'POST /auth/logout': { status: 204 },
  'GET /ping': { body: { status: 'ok' } },
});

/** Rutas para arrancar sin sesión. */
export const signedOut: Routes = {
  'POST /auth/refresh': { status: 401, body: { message: 'Sin sesión' } },
  'GET /ping': { body: { status: 'ok' } },
};
