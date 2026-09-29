import { beforeEach, describe, expect, it, vi } from 'vitest';
import { stubApi, testUser } from '../test/stubApi';
import { ApiError, getMe, login, logout, onSessionExpired } from './api';

const auth = (init?: RequestInit) => (init?.headers as Record<string, string>)?.Authorization;

/** `/me` solo acepta el token indicado. */
const meAccepting = (token: string) => (init?: RequestInit) =>
  auth(init) === `Bearer ${token}`
    ? { body: testUser }
    : { status: 401, body: { message: 'Token vencido' } };

async function signIn() {
  stubApi({
    'POST /auth/login': { body: { accessToken: 'token-1' } },
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

  it('ante un 401 renueva el token una vez y reintenta', async () => {
    await signIn();
    const fetchMock = stubApi({
      'POST /auth/refresh': { body: { accessToken: 'token-2' } },
      'GET /me': meAccepting('token-2'),
    });

    await expect(getMe()).resolves.toEqual(testUser);

    const calls = fetchMock.mock.calls.map(([url, init]) => `${init?.method} ${url}`);
    expect(calls).toEqual(['GET /api/me', 'POST /api/auth/refresh', 'GET /api/me']);
  });

  it('si la renovación falla avisa que la sesión venció', async () => {
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
    unsubscribe();
  });

  it('sin sesión un 401 no intenta renovar', async () => {
    const fetchMock = stubApi({ 'GET /me': { status: 401, body: { message: 'Sin sesión' } } });

    await expect(getMe()).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});
