import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedOut, stubApi } from '../test/stubApi';

async function renderRegister() {
  render(
    <MemoryRouter initialEntries={['/registro']}>
      <App />
    </MemoryRouter>,
  );
  await screen.findByRole('heading', { level: 1, name: 'Crear cuenta' });
}

async function fill(values: Partial<Record<string, string>>) {
  for (const [label, value] of Object.entries(values)) {
    if (value) await userEvent.type(screen.getByLabelText(label), value);
  }
}

const valid = {
  'Nombre y apellido': 'Ana Pérez',
  'Email institucional': 'ana.perez',
  Legajo: '1099999',
  Contraseña: 'secreta-123',
};

const submit = () => userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));

describe('RegisterPage', () => {
  it('pide todos los campos', async () => {
    const fetchMock = stubApi(signedOut);
    await renderRegister();

    await submit();

    expect(screen.getByLabelText('Nombre y apellido')).toHaveAccessibleDescription(
      'Ingresá tu nombre y apellido.',
    );
    expect(screen.getByLabelText('Email institucional')).toBeInvalid();
    expect(screen.getByLabelText('Legajo')).toHaveAccessibleDescription(
      'Está en tu credencial UADE. Ingresá tu legajo.',
    );
    expect(screen.getByLabelText('Contraseña')).toBeInvalid();
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/register', expect.anything());
  });

  it('muestra los requisitos de la contraseña antes de escribir', async () => {
    stubApi(signedOut);
    await renderRegister();

    expect(screen.getByLabelText('Contraseña')).toHaveAccessibleDescription(
      'Entre 8 y 64 caracteres. No exigimos símbolos ni mayúsculas.',
    );
  });

  it('rechaza una contraseña corta', async () => {
    const fetchMock = stubApi(signedOut);
    await renderRegister();

    await fill({ ...valid, Contraseña: 'corta' });
    await submit();

    expect(screen.getByLabelText('Contraseña')).toHaveAccessibleDescription(
      /La contraseña tiene que tener entre 8 y 64 caracteres\./,
    );
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/register', expect.anything());
  });

  // Se pega en vez de tipear: 65 teclas con userEvent.type hacían lento (y a veces timeout) el test.
  async function pastePassword(value: string) {
    await userEvent.click(screen.getByLabelText('Contraseña'));
    await userEvent.paste(value);
  }

  it('cuenta caracteres y no bytes: 64 ñ entran, 65 no', async () => {
    const fetchMock = stubApi({ ...signedOut, 'POST /auth/register': { status: 204 } });
    await renderRegister();

    await fill({ ...valid, Contraseña: '' });
    await pastePassword('ñ'.repeat(65));
    await submit();

    expect(screen.getByLabelText('Contraseña')).toHaveAccessibleDescription(
      /La contraseña tiene que tener entre 8 y 64 caracteres\./,
    );
    expect(fetchMock).not.toHaveBeenCalledWith('/api/auth/register', expect.anything());

    await userEvent.clear(screen.getByLabelText('Contraseña'));
    await pastePassword('ñ'.repeat(64));
    await submit();

    await screen.findByRole('heading', { name: 'Revisá tu email' });
  });

  it('cuenta tras normalizar con NFKC: la ñ descompuesta vale un carácter', async () => {
    const fetchMock = stubApi({ ...signedOut, 'POST /auth/register': { status: 204 } });
    await renderRegister();

    // n + tilde combinable (U+0303): 128 code points sin normalizar, 64 con NFKC. Los emojis valen 1.
    await fill({ ...valid, Contraseña: '' });
    await pastePassword('ñ'.repeat(56) + '🛗'.repeat(8));
    await submit();

    await screen.findByRole('heading', { name: 'Revisá tu email' });
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/register', expect.anything());
  });

  it('muestra en el campo el aviso de contraseña común que manda el servidor', async () => {
    stubApi({
      ...signedOut,
      'POST /auth/register': {
        status: 400,
        body: {
          message: 'La solicitud tiene campos inválidos',
          violations: [
            {
              field: 'password',
              message: 'Esa contraseña es muy común y fácil de adivinar. Elegí otra.',
            },
          ],
        },
      },
    });
    await renderRegister();

    await fill({ ...valid, Contraseña: 'contraseña123' });
    await submit();

    expect(await screen.findByRole('alert')).toHaveTextContent('Esa contraseña es muy común');
    expect(screen.getByLabelText('Contraseña')).toHaveAccessibleDescription(
      'Entre 8 y 64 caracteres. No exigimos símbolos ni mayúsculas. Esa contraseña es muy común y fácil de adivinar. Elegí otra.',
    );
  });

  it('valida el dominio del email y que el legajo sea numérico', async () => {
    stubApi(signedOut);
    await renderRegister();

    await fill({ ...valid, 'Email institucional': 'ana@gmail.com', Legajo: '10a' });
    await submit();

    expect(screen.getByText('Usá tu email @uade.edu.ar.')).toBeInTheDocument();
    expect(screen.getByLabelText('Legajo')).toHaveAccessibleDescription(
      'Está en tu credencial UADE. El legajo tiene que ser numérico.',
    );
  });

  it('al pegar el email completo manda el email una sola vez', async () => {
    const fetchMock = stubApi({ ...signedOut, 'POST /auth/register': { status: 204 } });
    await renderRegister();

    await fill({ ...valid, 'Email institucional': '' });
    await userEvent.click(screen.getByLabelText('Email institucional'));
    await userEvent.paste('ana.perez@uade.edu.ar');
    await submit();

    await screen.findByRole('heading', { name: 'Revisá tu email' });
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/auth/register')!;
    expect(JSON.parse(init!.body as string).email).toBe('ana.perez@uade.edu.ar');
  });

  it('dice dónde encontrar el legajo antes de escribir', async () => {
    stubApi(signedOut);
    await renderRegister();

    expect(screen.getByLabelText('Legajo')).toHaveAccessibleDescription(
      'Está en tu credencial UADE.',
    );
  });

  it('deja ver la contraseña', async () => {
    stubApi(signedOut);
    await renderRegister();

    await userEvent.click(screen.getByRole('button', { name: 'Mostrar contraseña' }));

    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('type', 'text');
  });

  it('los campos muestran un ejemplo', async () => {
    stubApi(signedOut);
    await renderRegister();

    const placeholders = Object.keys(valid).map((label) =>
      screen.getByLabelText(label).getAttribute('placeholder'),
    );
    expect(placeholders).toEqual(['Juana Martínez', 'jmartinez', 'Ej: 1234567', '••••••••']);
  });

  it('crea la cuenta con el tipo declarado y pide verificar el email', async () => {
    const fetchMock = stubApi({ ...signedOut, 'POST /auth/register': { status: 204 } });
    await renderRegister();

    expect(screen.getByRole('radio', { name: 'Estudiante' })).toBeChecked();
    expect(screen.getByText(/no te da prioridad/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '¿Tenés movilidad reducida?' })).toBeInTheDocument();

    await fill(valid);
    await userEvent.click(screen.getByRole('radio', { name: 'Docente' }));
    await submit();

    expect(await screen.findByRole('heading', { name: 'Revisá tu email' })).toBeInTheDocument();
    expect(screen.getByText('ana.perez@uade.edu.ar')).toBeInTheDocument();
    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/auth/register')!;
    expect(JSON.parse(init!.body as string)).toEqual({
      fullName: 'Ana Pérez',
      email: 'ana.perez@uade.edu.ar',
      legajo: '1099999',
      password: 'secreta-123',
      declaredUserType: 'TEACHER',
    });
  });

  it('muestra en el campo el email ya registrado', async () => {
    stubApi({
      ...signedOut,
      'POST /auth/register': {
        status: 409,
        body: {
          message: 'La cuenta ya existe',
          violations: [{ field: 'email', message: 'Ya hay una cuenta con ese email.' }],
        },
      },
    });
    await renderRegister();

    await fill(valid);
    await submit();

    // Se anuncia una sola vez: en el campo, sin repetirlo en un aviso general.
    const alerts = await screen.findAllByRole('alert');
    expect(alerts).toHaveLength(1);
    expect(alerts[0]).toHaveTextContent('Ya hay una cuenta con ese email.');
    expect(screen.getByLabelText('Email institucional')).toHaveAccessibleDescription(
      '@uade.edu.ar Ya hay una cuenta con ese email.',
    );
    expect(screen.getByLabelText('Nombre y apellido')).toHaveValue('Ana Pérez');
  });

  it('muestra el error general si no es de un campo', async () => {
    stubApi({
      ...signedOut,
      'POST /auth/register': { status: 500, body: { message: 'Error interno' } },
    });
    await renderRegister();

    await fill(valid);
    await submit();

    expect(await screen.findByRole('alert')).toHaveTextContent('Error interno');
  });
});
