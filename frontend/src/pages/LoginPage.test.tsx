import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedOut, stubApi, testUser } from '../test/stubApi';

const loginOk = {
  ...signedOut,
  'POST /auth/login': { body: { accessToken: 'token-1' } },
  'GET /me': { body: testUser },
};

async function renderLogin(path = '/login') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
  await screen.findByRole('heading', { level: 1, name: 'Bienvenido a SmartElevate' });
}

async function submit(email: string, password: string) {
  if (email) await userEvent.type(screen.getByLabelText('Email institucional'), email);
  if (password) await userEvent.type(screen.getByLabelText('Contraseña'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
}

describe('LoginPage', () => {
  it('valida los campos antes de enviar', async () => {
    const fetchMock = stubApi(loginOk);
    await renderLogin();

    await submit('', '');

    expect(screen.getByLabelText('Email institucional')).toHaveAccessibleDescription(
      '@uade.edu.ar Ingresá tu email institucional.',
    );
    expect(screen.getByLabelText('Contraseña')).toHaveAccessibleDescription(
      'Ingresá tu contraseña.',
    );
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/login', expect.anything());
  });

  it('solo acepta emails @uade.edu.ar', async () => {
    stubApi(loginOk);
    await renderLogin();

    await submit('ana@gmail.com', 'secreta');

    expect(screen.getByLabelText('Email institucional')).toBeInvalid();
    expect(screen.getByText('Usá tu email @uade.edu.ar.')).toBeInTheDocument();
  });

  it('al ingresar va al inicio', async () => {
    const fetchMock = stubApi(loginOk);
    await renderLogin();

    await submit('ana.perez', 'secreta');

    expect(await screen.findByRole('heading', { level: 1, name: 'Hola, Ana' })).toBeInTheDocument();
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/auth/login')!;
    expect(JSON.parse(init!.body as string)).toEqual({
      email: 'ana.perez@uade.edu.ar',
      password: 'secreta',
    });
  });

  it('el dominio queda fijo: se escribe solo el usuario y se anuncia el dominio', async () => {
    stubApi(signedOut);
    await renderLogin();

    const field = screen.getByLabelText('Email institucional');
    expect(field).toHaveAccessibleDescription('@uade.edu.ar');
    expect(field).toHaveAttribute('autocomplete', 'username');
    expect(field).toHaveAttribute('autocapitalize', 'none');
    expect(field).toHaveAttribute('spellcheck', 'false');
  });

  it('si se pega el email completo se queda con el usuario', async () => {
    const fetchMock = stubApi(loginOk);
    await renderLogin();

    await userEvent.click(screen.getByLabelText('Email institucional'));
    await userEvent.paste(' Ana.Perez@UADE.edu.ar ');
    expect(screen.getByLabelText('Email institucional')).toHaveValue('Ana.Perez');
    await submit('', 'secreta');

    await screen.findByRole('heading', { level: 1, name: 'Hola, Ana' });
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/auth/login')!;
    expect(JSON.parse(init!.body as string).email).toBe('Ana.Perez@uade.edu.ar');
  });

  it('no acepta espacios en el usuario', async () => {
    stubApi(loginOk);
    await renderLogin();

    await submit('ana perez', 'secreta');

    expect(screen.getByText('Escribilo sin espacios.')).toBeInTheDocument();
  });

  it('vuelve a la ruta privada que se quería visitar', async () => {
    stubApi(loginOk);
    await renderLogin('/reservar');

    await submit('ana.perez', 'secreta');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Reservar turno' }),
    ).toBeInTheDocument();
  });

  it('muestra el error del servidor sin perder lo tipeado', async () => {
    stubApi({
      ...signedOut,
      'POST /auth/login': { status: 401, body: { message: 'Email o contraseña incorrectos' } },
    });
    await renderLogin();

    await submit('ana.perez', 'incorrecta');

    expect(await screen.findByRole('alert')).toHaveTextContent('Email o contraseña incorrectos');
    expect(screen.getByLabelText('Email institucional')).toHaveValue('ana.perez');
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeEnabled();
  });

  it('"¿Olvidaste tu contraseña?" lleva a recuperar la contraseña', async () => {
    stubApi(signedOut);
    await renderLogin();

    await userEvent.click(screen.getByRole('link', { name: '¿Olvidaste tu contraseña?' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Recuperar contraseña' }),
    ).toBeInTheDocument();
  });

  it('muestra y oculta la contraseña, y la vuelve a ocultar al enviar', async () => {
    stubApi({
      ...signedOut,
      'POST /auth/login': { status: 401, body: { message: 'Email o contraseña incorrectos' } },
    });
    await renderLogin();
    const field = screen.getByLabelText('Contraseña');
    expect(field).toHaveAttribute('type', 'password');

    await userEvent.click(screen.getByRole('button', { name: 'Mostrar contraseña' }));
    expect(field).toHaveAttribute('type', 'text');
    await userEvent.click(screen.getByRole('button', { name: 'Ocultar contraseña' }));
    expect(field).toHaveAttribute('type', 'password');

    await userEvent.click(screen.getByRole('button', { name: 'Mostrar contraseña' }));
    await submit('ana.perez', 'secreta');

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(field).toHaveAttribute('type', 'password');
  });

  it('linkea al registro', async () => {
    stubApi(signedOut);
    await renderLogin();

    expect(screen.getByRole('link', { name: 'Registrate' })).toHaveAttribute('href', '/registro');
  });

  it('los campos muestran un ejemplo sin reemplazar la etiqueta', async () => {
    stubApi(signedOut);
    await renderLogin();

    expect(screen.getByLabelText('Email institucional')).toHaveAttribute(
      'placeholder',
      'jmartinez',
    );
    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('placeholder', '••••••••');
  });

  it('muestra el logo de UADE al pie', async () => {
    stubApi(signedOut);
    await renderLogin();

    expect(screen.getByRole('img', { name: 'UADE' })).toBeInTheDocument();
  });
});
