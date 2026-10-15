import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { signedOut, stubApi } from '../test/stubApi';

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('VerifyEmailPage', () => {
  it('verifica con el token de la URL y ofrece ingresar', async () => {
    const fetchMock = stubApi({ ...signedOut, 'POST /auth/verify': { status: 204 } });
    renderAt('/verificar-email?token=abc');

    expect(await screen.findByText('Listo, verificamos tu email')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a ingresar' })).toHaveAttribute('href', '/login');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/verify',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ token: 'abc' }) }),
    );
  });

  it('muestra el motivo del 400 y permite reenviar', async () => {
    const fetchMock = stubApi({
      ...signedOut,
      'POST /auth/verify': { status: 400, body: { message: 'El link venció.' } },
      'POST /auth/resend-verification': { status: 202 },
    });
    renderAt('/verificar-email?token=viejo');

    expect(await screen.findByRole('alert')).toHaveTextContent('El link venció.');
    await userEvent.type(screen.getByLabelText('Email institucional'), 'ana.perez');
    await userEvent.click(screen.getByRole('button', { name: 'Reenviar email de verificación' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Si ana.perez@uade.edu.ar tiene una cuenta sin verificar',
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/resend-verification',
      expect.objectContaining({ body: JSON.stringify({ email: 'ana.perez@uade.edu.ar' }) }),
    );
  });

  it('sin token no llama al servidor', async () => {
    const fetchMock = stubApi(signedOut);
    renderAt('/verificar-email');

    expect(await screen.findByRole('alert')).toHaveTextContent('Este link no es válido');
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/verify', expect.anything());
  });

  it('el login con 403 ofrece reenviar el email a esa cuenta', async () => {
    const fetchMock = stubApi({
      ...signedOut,
      'POST /auth/login': { status: 403, body: { message: 'Verificá tu email.' } },
      'POST /auth/resend-verification': { status: 202 },
    });
    renderAt('/login');

    await userEvent.type(await screen.findByLabelText('Email institucional'), 'ana.perez');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'secreta');
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
    await userEvent.click(
      await screen.findByRole('button', { name: 'Reenviar email de verificación' }),
    );

    expect(await screen.findByRole('status')).toHaveTextContent('ana.perez@uade.edu.ar');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/resend-verification',
      expect.objectContaining({ body: JSON.stringify({ email: 'ana.perez@uade.edu.ar' }) }),
    );
  });

  it('con 429 muestra cuánto esperar y deshabilita el botón', async () => {
    stubApi({
      ...signedOut,
      'POST /auth/verify': { status: 400, body: { message: 'Ya se usó.' } },
    });
    // stubApi no manda headers: respondemos el 429 con Retry-After a mano.
    const base = globalThis.fetch;
    vi.stubGlobal('fetch', async (url: string, init?: RequestInit) =>
      url === '/api/auth/resend-verification'
        ? new Response(JSON.stringify({ message: 'Esperá.' }), {
            status: 429,
            headers: { 'Retry-After': '45' },
          })
        : base(url, init),
    );
    renderAt('/verificar-email?token=x');

    await userEvent.type(await screen.findByLabelText('Email institucional'), 'ana.perez');
    await userEvent.click(screen.getByRole('button', { name: 'Reenviar email de verificación' }));

    expect(await screen.findByRole('button', { name: 'Podés pedir otro en 45 s' })).toBeDisabled();
    expect(screen.getByText('Ya te mandamos un email hace poco.')).toBeInTheDocument();
  });
});
