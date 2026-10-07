import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedOut, stubApi } from '../test/stubApi';

async function renderForgot() {
  render(
    <MemoryRouter initialEntries={['/recuperar']}>
      <App />
    </MemoryRouter>,
  );
  await screen.findByRole('heading', { level: 1, name: 'Recuperar contraseña' });
}

describe('ForgotPasswordPage', () => {
  it('valida el email institucional antes de enviar', async () => {
    const fetchMock = stubApi(signedOut);
    await renderForgot();

    await userEvent.type(screen.getByLabelText('Email institucional'), 'ana@gmail.com');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar link' }));

    expect(screen.getByLabelText('Email institucional')).toBeInvalid();
    expect(screen.getByText('Usá tu email @uade.edu.ar.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/password/forgot', expect.anything());
  });

  it('con 202 sin cuerpo muestra la confirmación sin decir si la cuenta existe', async () => {
    const fetchMock = stubApi({ ...signedOut, 'POST /auth/password/forgot': { status: 202 } });
    await renderForgot();

    await userEvent.type(screen.getByLabelText('Email institucional'), 'ana.perez');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar link' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Si ana.perez@uade.edu.ar tiene una cuenta, te mandamos un link',
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/password/forgot',
      expect.objectContaining({ body: JSON.stringify({ email: 'ana.perez@uade.edu.ar' }) }),
    );
  });

  it('al pegar el email completo se queda con el usuario', async () => {
    const fetchMock = stubApi({ ...signedOut, 'POST /auth/password/forgot': { status: 202 } });
    await renderForgot();

    await userEvent.click(screen.getByLabelText('Email institucional'));
    await userEvent.paste('ana.perez@uade.edu.ar');
    expect(screen.getByLabelText('Email institucional')).toHaveValue('ana.perez');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar link' }));

    await screen.findByRole('status');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/password/forgot',
      expect.objectContaining({ body: JSON.stringify({ email: 'ana.perez@uade.edu.ar' }) }),
    );
  });

  it('muestra el error del servidor', async () => {
    stubApi({
      ...signedOut,
      'POST /auth/password/forgot': { status: 429, body: { message: 'Probá en unos minutos.' } },
    });
    await renderForgot();

    await userEvent.type(screen.getByLabelText('Email institucional'), 'ana.perez@uade.edu.ar');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar link' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Probá en unos minutos.');
  });
});
