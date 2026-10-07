import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedOut, stubApi } from '../test/stubApi';

async function renderReset(path = '/recuperar/nueva?token=abc123') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
  await screen.findByRole('heading', { level: 1, name: 'Contraseña nueva' });
}

async function submit(password: string, repeat: string) {
  if (password) await userEvent.type(screen.getByLabelText('Contraseña nueva'), password);
  if (repeat) await userEvent.type(screen.getByLabelText('Repetí la contraseña'), repeat);
  await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
}

describe('ResetPasswordPage', () => {
  it('sin token avisa que el link no sirve y ofrece pedir otro', async () => {
    stubApi(signedOut);
    await renderReset('/recuperar/nueva');

    expect(screen.getByRole('alert')).toHaveTextContent('Este link no es válido');
    expect(screen.getByRole('link', { name: 'Pedir otro link' })).toHaveAttribute(
      'href',
      '/recuperar',
    );
  });

  it('las dos contraseñas tienen que coincidir', async () => {
    const fetchMock = stubApi(signedOut);
    await renderReset();

    await submit('nueva-123', 'otra-456');

    expect(screen.getByLabelText('Repetí la contraseña')).toHaveAccessibleDescription(
      'Las contraseñas no coinciden.',
    );
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/password/reset', expect.anything());
  });

  it('manda el token y la contraseña, y confirma el cambio', async () => {
    const fetchMock = stubApi({ ...signedOut, 'POST /auth/password/reset': { status: 204 } });
    await renderReset();

    await submit('nueva-123', 'nueva-123');

    expect(await screen.findByRole('status')).toHaveTextContent('Listo, cambiaste tu contraseña');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/password/reset',
      expect.objectContaining({
        body: JSON.stringify({ token: 'abc123', newPassword: 'nueva-123' }),
      }),
    );
  });

  it('el error de la política de contraseña va al lado del campo', async () => {
    stubApi({
      ...signedOut,
      'POST /auth/password/reset': {
        status: 400,
        body: {
          message: 'Datos inválidos',
          violations: [{ field: 'newPassword', message: 'Usá al menos 8 caracteres.' }],
        },
      },
    });
    await renderReset();

    await submit('corta', 'corta');

    expect(await screen.findByText('Usá al menos 8 caracteres.')).toBeInTheDocument();
  });

  it('con el link vencido muestra el error y ofrece pedir otro', async () => {
    stubApi({
      ...signedOut,
      'POST /auth/password/reset': {
        status: 400,
        body: { message: 'El link venció o ya se usó. Pedí uno nuevo.' },
      },
    });
    await renderReset();

    await submit('nueva-123', 'nueva-123');

    expect(await screen.findByRole('alert')).toHaveTextContent('El link venció o ya se usó');
    expect(screen.getByRole('link', { name: 'Pedir otro link' })).toBeInTheDocument();
  });
});
