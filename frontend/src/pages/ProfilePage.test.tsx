import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedIn, stubApi, testUser } from '../test/stubApi';

function stubProfile() {
  return stubApi(signedIn({ ...testUser, declaredUserType: 'TEACHER' }));
}

function renderProfile() {
  render(
    <MemoryRouter initialEntries={['/perfil']}>
      <App />
    </MemoryRouter>,
  );
}

describe('ProfilePage', () => {
  it('muestra los datos del usuario y las opciones, con "Perfil" activo', async () => {
    stubProfile();
    renderProfile();

    expect(await screen.findByRole('heading', { name: 'Ana Pérez' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Mi perfil' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Volver' })).not.toBeInTheDocument();
    expect(screen.getByText('Docente')).toBeInTheDocument();
    expect(screen.getByText('Legajo 1099999')).toBeInTheDocument();
    expect(screen.getByText('ana.perez@uade.edu.ar')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'UADE' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mis viajes' })).toHaveAttribute(
      'href',
      '/perfil/viajes',
    );
    expect(screen.getByRole('link', { name: 'Notificaciones' })).toHaveAttribute(
      'href',
      '/perfil/notificaciones',
    );
    // BottomNav y TopBar (el container query muestra una sola).
    for (const tab of screen.getAllByRole('link', { name: 'Perfil' })) {
      expect(tab).toHaveAttribute('aria-current', 'page');
    }
  });

  it.each([
    ['STUDENT', 'Estudiante'],
    ['TEACHER', 'Docente'],
  ] as const)('muestra el tipo de usuario %s con texto', async (declaredUserType, label) => {
    stubApi(signedIn({ ...testUser, declaredUserType }));
    renderProfile();

    expect(await screen.findByText(label)).toBeInTheDocument();
  });

  it('muestra la tarjeta informativa de acceso prioritario, sin pedir certificados', async () => {
    stubProfile();
    renderProfile();

    const card = await screen.findByRole('region', { name: 'Acceso prioritario' });
    expect(card).toHaveTextContent(
      'Si tenés movilidad reducida, temporal o permanente, informalo en la Oficina de Alumnos (OFALU) de UADE. SmartElevate no te pide certificados ni datos de salud.',
    );
    expect(within(card).queryByRole('button')).not.toBeInTheDocument();
    expect(document.querySelector('input[type="file"]')).toBeNull();
    expect(screen.queryByLabelText(/certificado/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('con prioridad de docente muestra hasta cuándo vale, en hora de Buenos Aires', async () => {
    // 02:59 UTC del 1/4 es todavía 31/3 en Buenos Aires.
    stubApi(
      signedIn({
        ...testUser,
        declaredUserType: 'TEACHER',
        priority: 'TEACHER',
        priorityUntil: '2027-04-01T02:59:59Z',
      }),
    );
    renderProfile();

    expect(
      await screen.findByText('Prioridad de docente: vigente hasta el 31/03'),
    ).toBeInTheDocument();
  });

  it('con prioridad de docente sin vencimiento dice solo "vigente"', async () => {
    stubApi(
      signedIn({
        ...testUser,
        declaredUserType: 'TEACHER',
        priority: 'TEACHER',
        priorityUntil: null,
      }),
    );
    renderProfile();

    expect(await screen.findByText('Prioridad de docente: vigente')).toBeInTheDocument();
  });

  it('"Cerrar sesión" vuelve a /login', async () => {
    const fetchMock = stubProfile();
    renderProfile();

    const logout = await screen.findByRole('button', { name: 'Cerrar sesión' });
    // Es una acción, no un destino: va fuera de la navegación del perfil.
    expect(screen.getByRole('navigation', { name: 'Opciones del perfil' })).not.toContainElement(
      logout,
    );
    await userEvent.click(logout);

    expect(
      await screen.findByRole('heading', {
        level: 1,
        name: 'Te damos la bienvenida a SmartElevate',
      }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', expect.anything());
  });
});
