import { beforeEach, describe, expect, it, vi } from 'vitest';
import { stubApi, testUser, tokens } from '../test/stubApi';
import { ApiError, getMe, login, logout, onSessionExpired, restoreSession } from './api';

const auth = (init?: RequestInit) => (init?.headers as Record<string, string>)?.Authorization;
const body = (init?: RequestInit) => JSON.parse(init!.body as string);

/** `/me` solo acepta el token indicado. */
const meAccepting = (token: string) => (init?: RequestInit) =>
  auth(init) === `Bearer ${token}`
    ? { body: testUser }
    : { status: 401, body: { message: 'Token vencido' } };

async function signIn() {
  stubApi({
    'POST /auth/login': { body: tokens(1) },
    'GET /me': meAccepting('token-1'),
  });
  await login({ email: testUser.email, password: 'secreta' });
}

describe('sesión en services/api', () => {
  beforeEach(async () => {
    stubApi({ 'POST /auth/logout': { status: 204 } });
    await logout();
  });

  it('login guarda el token y lo manda en cada request', async () => {
    await signIn();

    await expect(getMe()).resolves.toEqual(testUser);
  });

  it('ante un 401 renueva con el refresh token, lo rota y reintenta', async () => {
    await signIn();
    const fetchMock = stubApi({
      'POST /auth/refresh': { body: tokens(2) },
      'GET /me': meAccepting('token-2'),
    });

    await expect(getMe()).resolves.toEqual(testUser);

    const calls = fetchMock.mock.calls.map(([url, init]) => `${init?.method} ${url}`);
    expect(calls).toEqual(['GET /api/me', 'POST /api/auth/refresh', 'GET /api/me']);
    expect(body(fetchMock.mock.calls[1][1])).toEqual({ refreshToken: 'refresh-1' });
    expect(sessionStorage.getItem('smartelevate.refresh')).toBe('refresh-2');
  });

  it('requests simultáneos con el access vencido comparten un único refresh', async () => {
    await signIn();
    const fetchMock = stubApi({
      'POST /auth/refresh': { body: tokens(2) },
      'GET /me': meAccepting('token-2'),
    });

    await Promise.all([getMe(), getMe(), getMe()]);

    const refreshes = fetchMock.mock.calls.filter(([url]) => url === '/api/auth/refresh');
    expect(refreshes).toHaveLength(1);
  });

  it('si el refresh es inválido o reutilizado cierra la sesión y avisa', async () => {
    await signIn();
    stubApi({
      'POST /auth/refresh': { status: 401, body: { message: 'Refresh vencido' } },
      'GET /me': meAccepting('token-2'),
    });
    const expired = vi.fn();
    const unsubscribe = onSessionExpired(expired);

    const error = await getMe().catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
    expect(expired).toHaveBeenCalledOnce();
    expect(sessionStorage.getItem('smartelevate.refresh')).toBeNull();
    unsubscribe();
  });

  it('sin sesión un 401 no intenta renovar', async () => {
    const fetchMock = stubApi({ 'GET /me': { status: 401, body: { message: 'Sin sesión' } } });

    await expect(getMe()).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('al recargar la pestaña recupera la sesión con el refresh token guardado', async () => {
    await signIn();
    const fetchMock = stubApi({
      'POST /auth/refresh': { body: tokens(2) },
      'GET /me': meAccepting('token-2'),
    });

    await expect(restoreSession()).resolves.toEqual(testUser);
    expect(body(fetchMock.mock.calls[0][1])).toEqual({ refreshToken: 'refresh-1' });
  });

  it('sin refresh token guardado no hay sesión que recuperar', async () => {
    const fetchMock = stubApi({});

    await expect(restoreSession()).resolves.toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('logout revoca el refresh token en el servidor y borra lo guardado', async () => {
    await signIn();
    const fetchMock = stubApi({ 'POST /auth/logout': { status: 204 } });

    await logout();

    expect(body(fetchMock.mock.calls[0][1])).toEqual({ refreshToken: 'refresh-1' });
    expect(sessionStorage.getItem('smartelevate.refresh')).toBeNull();
  });
});
