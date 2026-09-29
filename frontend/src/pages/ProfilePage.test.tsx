import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedIn, stubApi, testUser } from '../test/stubApi';
import type { PriorityRequest, PriorityUploadRules } from '../types/pending';

const rules: PriorityUploadRules = {
  maxSizeBytes: 5 * 1024 * 1024,
  acceptedTypes: ['application/pdf', 'image/jpeg', 'image/png'],
};

const pending: PriorityRequest = {
  category: 'REDUCED_MOBILITY',
  status: 'PENDING',
  submittedAt: '2026-09-29T15:00:00Z',
  expiresAt: null,
};

function stubProfile(request: PriorityRequest | null, extra = {}) {
  return stubApi({
    ...signedIn({ ...testUser, declaredUserType: 'TEACHER' }),
    'GET /priority-requests/me': { body: request },
    'GET /priority-requests/upload-rules': { body: rules },
    ...extra,
  });
}

function renderProfile() {
  render(
    <MemoryRouter initialEntries={['/perfil']}>
      <App />
    </MemoryRouter>,
  );
}

const pdf = (size = 1024) =>
  new File([new Uint8Array(size)], 'certificado.pdf', { type: 'application/pdf' });

const chooseFile = async (file: File) =>
  userEvent.upload(await screen.findByLabelText(/Elegí tu certificado/), file);

const card = () => screen.findByRole('region', { name: 'Acceso prioritario' });

describe('ProfilePage', () => {
  it('muestra los datos del usuario y las opciones, con "Perfil" activo', async () => {
    stubProfile(null);
    renderProfile();

    expect(await screen.findByRole('heading', { name: 'Ana Pérez' })).toBeInTheDocument();
    expect(screen.getByText('Docente')).toBeInTheDocument();
    expect(screen.getByText('Legajo 1099999')).toBeInTheDocument();
    expect(screen.getByText('ana.perez@uade.edu.ar')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mis viajes' })).toHaveAttribute(
      'href',
      '/perfil/viajes',
    );
    expect(screen.getByRole('link', { name: 'Notificaciones' })).toHaveAttribute(
      'href',
      '/perfil/notificaciones',
    );
    expect(screen.getByRole('link', { name: 'Perfil' })).toHaveAttribute('aria-current', 'page');
  });

  it('envía el certificado con el consentimiento y pasa a pendiente', async () => {
    let sent: FormData | undefined;
    stubProfile(null, {
      'POST /priority-requests': (init?: RequestInit) => {
        sent = init?.body as FormData;
        return { status: 201, body: pending };
      },
    });
    renderProfile();

    expect(await within(await card()).findByText('Sin solicitud')).toBeInTheDocument();
    expect(screen.getByText('PDF, JPG o PNG, hasta 5 MB')).toBeInTheDocument();
    const file = pdf();
    await chooseFile(file);
    expect(screen.getByText('certificado.pdf')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: /Ley 25\.326/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar solicitud' }));

    expect(await within(await card()).findByText('Pendiente de validación')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Enviamos tu solicitud.');
    expect(sent?.get('consentAccepted')).toBe('true');
    expect(sent?.get('certificate')).toBe(file);
    expect(screen.queryByLabelText(/Elegí tu certificado/)).not.toBeInTheDocument();
    expect(screen.queryByText('certificado.pdf')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enviar solicitud' })).not.toBeInTheDocument();
  });

  it('sin tildar el consentimiento no se puede enviar', async () => {
    stubProfile(null);
    renderProfile();

    await chooseFile(pdf());

    const submit = screen.getByRole('button', { name: 'Enviar solicitud' });
    expect(submit).toBeDisabled();
    await userEvent.click(screen.getByRole('checkbox', { name: /Ley 25\.326/ }));
    expect(submit).toBeEnabled();
  });

  it('rechaza un archivo muy grande antes de subirlo', async () => {
    stubProfile(null);
    renderProfile();

    await chooseFile(pdf(rules.maxSizeBytes + 1));

    expect(screen.getByRole('alert')).toHaveTextContent('El archivo es muy grande');
    expect(screen.queryByText('certificado.pdf')).not.toBeInTheDocument();
  });

  it('rechaza un tipo de archivo no permitido', async () => {
    stubProfile(null);
    renderProfile();
    const input = await screen.findByLabelText(/Elegí tu certificado/);

    // `applyAccept: false`: simula que el selector del sistema deja elegir cualquier archivo.
    await userEvent
      .setup({ applyAccept: false })
      .upload(input, new File(['x'], 'foto.gif', { type: 'image/gif' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Ese tipo de archivo no sirve');
  });

  it('"Quitar" descarta el archivo elegido', async () => {
    stubProfile(null);
    renderProfile();
    await chooseFile(pdf());

    await userEvent.click(screen.getByRole('button', { name: 'Quitar certificado.pdf' }));

    expect(screen.queryByText('certificado.pdf')).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Elegí tu certificado/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar solicitud' })).toBeDisabled();
  });

  it('muestra el error del servidor si no se pudo enviar', async () => {
    stubProfile(null, {
      'POST /priority-requests': {
        status: 415,
        body: { message: 'El archivo tiene que ser PDF, JPG o PNG.' },
      },
    });
    renderProfile();
    await chooseFile(pdf());
    await userEvent.click(screen.getByRole('checkbox', { name: /Ley 25\.326/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar solicitud' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('tiene que ser PDF, JPG o PNG');
    expect(screen.getByText('certificado.pdf')).toBeInTheDocument();
  });

  it.each<[string, PriorityRequest, string, boolean]>([
    ['pendiente', pending, 'Pendiente de validación', false],
    [
      'aprobada',
      { ...pending, status: 'APPROVED', expiresAt: '2999-10-05T15:00:00Z' },
      'Aprobado (vence el 05/10)',
      false,
    ],
    [
      'vencida',
      { ...pending, status: 'APPROVED', expiresAt: '2020-10-05T15:00:00Z' },
      'Venció el 05/10',
      true,
    ],
    ['rechazada', { ...pending, status: 'REJECTED' }, 'Rechazado', true],
  ])('con una solicitud %s muestra su estado', async (_, request, label, canUpload) => {
    stubProfile(request);
    renderProfile();

    expect(await within(await card()).findByText(label)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Elegí tu certificado/) !== null).toBe(canUpload);
  });

  it('muestra el error si no se pudo cargar la solicitud y permite reintentar', async () => {
    let calls = 0;
    stubProfile(null, {
      'GET /priority-requests/me': () =>
        ++calls === 1 ? { status: 500, body: { message: 'Error inesperado' } } : { body: null },
    });
    renderProfile();

    expect(await screen.findByRole('alert')).toHaveTextContent('Error inesperado');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await within(await card()).findByText('Sin solicitud')).toBeInTheDocument();
  });

  it('"Cerrar sesión" vuelve a /login', async () => {
    const fetchMock = stubProfile(null);
    renderProfile();

    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar sesión' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Bienvenido a SmartElevate' }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', expect.anything());
  });
});
