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

  it('cada campo deja ver su contraseña', async () => {
    stubApi(signedOut);
    await renderReset();

    const [first, second] = screen.getAllByRole('button', { name: 'Mostrar contraseña' });
    await userEvent.click(second);

    expect(screen.getByLabelText('Repetí la contraseña')).toHaveAttribute('type', 'text');
    expect(screen.getByLabelText('Contraseña nueva')).toHaveAttribute('type', 'password');
    expect(first).toBeInTheDocument();
  });

  it('muestra los requisitos de la contraseña antes de escribir', async () => {
    stubApi(signedOut);
    await renderReset();

    expect(screen.getByLabelText('Contraseña nueva')).toHaveAccessibleDescription(
      'Entre 8 y 64 caracteres. No exigimos símbolos ni mayúsculas.',
    );
  });

  it('rechaza una contraseña corta sin llamar al servidor', async () => {
    const fetchMock = stubApi(signedOut);
    await renderReset();

    await submit('corta', 'corta');

    expect(screen.getByLabelText('Contraseña nueva')).toHaveAccessibleDescription(
      /La contraseña tiene que tener entre 8 y 64 caracteres\./,
    );
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/password/reset', expect.anything());
  });

  // Se pega en vez de tipear: con userEvent.type, 65 caracteres por campo hacían lento el test.
  async function pasteAndSubmit(password: string) {
    for (const label of ['Contraseña nueva', 'Repetí la contraseña']) {
      await userEvent.clear(screen.getByLabelText(label));
      await userEvent.click(screen.getByLabelText(label));
      await userEvent.paste(password);
    }
    await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
  }

  it('cuenta caracteres y no bytes: 64 ñ entran, 65 no', async () => {
    const fetchMock = stubApi({ ...signedOut, 'POST /auth/password/reset': { status: 204 } });
    await renderReset();

    await pasteAndSubmit('ñ'.repeat(65));

    expect(screen.getByLabelText('Contraseña nueva')).toHaveAccessibleDescription(
      /La contraseña tiene que tener entre 8 y 64 caracteres\./,
    );
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/password/reset', expect.anything());

    await pasteAndSubmit('ñ'.repeat(64));

    expect(await screen.findByRole('status')).toHaveTextContent('Listo, cambiaste tu contraseña');
  });

  it('el aviso de contraseña común del servidor va al lado del campo', async () => {
    stubApi({
      ...signedOut,
      'POST /auth/password/reset': {
        status: 400,
        body: {
          message: 'La solicitud tiene campos inválidos',
          violations: [
            {
              field: 'newPassword',
              message: 'Esa contraseña es muy común y fácil de adivinar. Elegí otra.',
            },
          ],
        },
      },
    });
    await renderReset();

    await pasteAndSubmit('qwertyuiop');

    expect(await screen.findByText(/Esa contraseña es muy común/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Pedir otro link' })).not.toBeInTheDocument();
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

    await submit('nueva-123', 'nueva-123');

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
