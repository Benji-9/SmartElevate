import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { signedOut, stubApi } from '../test/stubApi';

const NOW = new Date('2026-10-14T15:40:00Z');
const KEY = 'smartelevate.kiosk.33';
type Reply = { status?: number; body?: unknown; headers?: Record<string, string> };

/** Token que vence a los 30 s (o lo que diga `reply`), contando los pedidos. */
function renderKiosk(reply?: (n: number) => Reply | undefined) {
  let n = 0;
  const fetchMock = stubApi({
    ...signedOut,
    'GET /kiosks/me/qr-token': () => {
      n += 1;
      return (
        reply?.(n) ?? {
          body: { token: `token-${n}`, expiresAt: new Date(Date.now() + 30_000).toISOString() },
        }
      );
    },
  });
  render(
    <MemoryRouter initialEntries={['/qr/33']}>
      <App />
    </MemoryRouter>,
  );
  const qrCalls = () =>
    fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/kiosks/me/qr-token'));
  return { qrCalls };
}

const qrPath = async () =>
  (await screen.findByRole('img', { name: 'Código QR del ascensor 33 para hacer check-in' }))
    .querySelector('path')!
    .getAttribute('d');
const advance = (ms: number) => act(() => vi.advanceTimersByTimeAsync(ms));

describe('KioskPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true, now: NOW });
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('pide la credencial una vez, la guarda en el dispositivo y no la muestra', async () => {
    const { qrCalls } = renderKiosk();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Kiosco del ascensor 33' }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Guardar y mostrar el QR' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Pegá la credencial del kiosco.');

    await user.type(screen.getByLabelText('Credencial del kiosco'), 's3cr3t');
    await user.click(screen.getByRole('button', { name: 'Guardar y mostrar el QR' }));

    expect(await qrPath()).toBeTruthy();
    expect(localStorage.getItem(KEY)).toBe('s3cr3t');
    expect(screen.queryByText(/s3cr3t/)).not.toBeInTheDocument();
    // La credencial del kiosco, no la sesión de una persona.
    const [, init] = qrCalls()[0];
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer s3cr3t');
  });

  it('renueva el QR al llegar a expiresAt', async () => {
    localStorage.setItem(KEY, 's3cr3t');
    const { qrCalls } = renderKiosk();

    const first = await qrPath();
    await advance(29_000);
    expect(qrCalls()).toHaveLength(1);

    await advance(1_000);
    expect(qrCalls()).toHaveLength(2);
    expect(await qrPath()).not.toBe(first);
  });

  it('mide el vencimiento con el reloj del servidor (header Date)', async () => {
    localStorage.setItem(KEY, 's3cr3t');
    // El reloj de la tablet va 20 s atrasado: al token le quedan 10 s, no 30.
    const { qrCalls } = renderKiosk(() => ({
      headers: { Date: new Date(Date.now() + 20_000).toUTCString() },
      body: { token: 'token', expiresAt: new Date(Date.now() + 30_000).toISOString() },
    }));

    await qrPath();
    await advance(10_000);
    expect(qrCalls()).toHaveLength(2);
  });

  it('con la credencial revocada (401) avisa, deja de pedir y permite borrarla', async () => {
    localStorage.setItem(KEY, 'revocada');
    const { qrCalls } = renderKiosk(() => ({
      status: 401,
      body: { code: 'KIOSK_UNAUTHORIZED', message: 'Credencial de kiosco inválida' },
    }));
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    expect(await screen.findByRole('alert')).toHaveTextContent('Este kiosco no está habilitado');
    await advance(5 * 60_000);
    expect(qrCalls()).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: 'Borrar credencial' }));
    expect(screen.getByLabelText('Credencial del kiosco')).toBeInTheDocument();
    expect(localStorage.getItem(KEY)).toBeNull();
  });

  it('con 429 espera lo que pide Retry-After antes de volver a pedir', async () => {
    localStorage.setItem(KEY, 's3cr3t');
    const { qrCalls } = renderKiosk((n) =>
      n === 1
        ? { status: 429, headers: { 'Retry-After': '20' }, body: { message: 'Too Many Requests' } }
        : undefined,
    );

    expect(
      await screen.findByText('Hicimos demasiados pedidos. Reintentamos en 20 s.'),
    ).toBeInTheDocument();
    await advance(19_000);
    expect(qrCalls()).toHaveLength(1);

    await advance(1_000);
    expect(await qrPath()).toBeTruthy();
    expect(qrCalls()).toHaveLength(2);
  });

  it('borrar la credencial pide confirmación', async () => {
    localStorage.setItem(KEY, 's3cr3t');
    renderKiosk();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await qrPath();

    await user.click(screen.getByRole('button', { name: 'Borrar credencial' }));
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(localStorage.getItem(KEY)).toBe('s3cr3t');

    await user.click(screen.getByRole('button', { name: 'Borrar credencial' }));
    await user.click(screen.getByRole('button', { name: 'Sí, borrar' }));
    expect(localStorage.getItem(KEY)).toBeNull();
    expect(screen.getByLabelText('Credencial del kiosco')).toBeInTheDocument();
  });
});
