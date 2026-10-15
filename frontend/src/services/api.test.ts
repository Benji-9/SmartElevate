import { beforeEach, describe, expect, it, vi } from 'vitest';
import { stubApi, testUser, tokens } from '../test/stubApi';
import { ApiError, getMe, login, logout, onSessionExpired, restoreSession } from './api';

const auth = (init?: RequestInit) => (init?.headers as Record<string, string>)?.Authorization;
const header = (init: RequestInit | undefined, name: string) =>
  (init?.headers as Record<string, string>)?.[name];

/** `/users/me` solo acepta el token indicado. */
const meAccepting = (token: string) => (init?: RequestInit) =>
  auth(init) === `Bearer ${token}`
    ? { body: testUser }
    : { status: 401, body: { message: 'Token vencido' } };

async function signIn() {
  stubApi({
    'POST /auth/login': { body: tokens(1) },
    'GET /users/me': meAccepting('token-1'),
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

  it('ante un 401 renueva con la cookie del refresh y reintenta', async () => {
    await signIn();
    const fetchMock = stubApi({
      'POST /auth/refresh': { body: tokens(2) },
      'GET /users/me': meAccepting('token-2'),
    });

    await expect(getMe()).resolves.toEqual(testUser);

    const calls = fetchMock.mock.calls.map(([url, init]) => `${init?.method} ${url}`);
    expect(calls).toEqual(['GET /api/users/me', 'POST /api/auth/refresh', 'GET /api/users/me']);
    const refresh = fetchMock.mock.calls[1][1];
    expect(refresh?.credentials).toBe('include');
    expect(header(refresh, 'X-Requested-With')).toBe('SmartElevate');
    expect(refresh?.body).toBeUndefined();
  });

  it('requests simultáneos con el access vencido comparten un único refresh', async () => {
    await signIn();
    const fetchMock = stubApi({
      'POST /auth/refresh': { body: tokens(2) },
      'GET /users/me': meAccepting('token-2'),
    });

    await Promise.all([getMe(), getMe(), getMe()]);

    const refreshes = fetchMock.mock.calls.filter(([url]) => url === '/api/auth/refresh');
    expect(refreshes).toHaveLength(1);
  });

  it('si el refresh es inválido o reutilizado cierra la sesión y avisa', async () => {
    await signIn();
    stubApi({
      'POST /auth/refresh': { status: 401, body: { message: 'Refresh vencido' } },
      'GET /users/me': meAccepting('token-2'),
    });
    const expired = vi.fn();
    const unsubscribe = onSessionExpired(expired);

    const error = await getMe().catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
    expect(expired).toHaveBeenCalledOnce();
    unsubscribe();
  });

  it('sin sesión un 401 no intenta renovar', async () => {
    const fetchMock = stubApi({
      'GET /users/me': { status: 401, body: { message: 'Sin sesión' } },
    });

    await expect(getMe()).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('al recargar la pestaña recupera la sesión con la cookie del refresh', async () => {
    await signIn();
    const fetchMock = stubApi({
      'POST /auth/refresh': { body: tokens(2) },
      'GET /users/me': meAccepting('token-2'),
    });

    await expect(restoreSession()).resolves.toEqual(testUser);
    expect(fetchMock.mock.calls[0][1]?.credentials).toBe('include');
  });

  it('sin cookie el refresh da 401 y no hay sesión que recuperar', async () => {
    const fetchMock = stubApi({
      'POST /auth/refresh': { status: 401, body: { message: 'Sin sesión' } },
    });

    await expect(restoreSession()).resolves.toBeNull();
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('logout manda la cookie con el header CSRF y borra el access token', async () => {
    await signIn();
    const fetchMock = stubApi({ 'POST /auth/logout': { status: 204 } });

    await logout();

    const init = fetchMock.mock.calls[0][1];
    expect(init?.credentials).toBe('include');
    expect(header(init, 'X-Requested-With')).toBe('SmartElevate');
    await expect(getMe()).rejects.toBeInstanceOf(ApiError);
  });
});
