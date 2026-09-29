import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { signedIn, signedOut, stubApi, testUser } from './test/stubApi';

function renderAt(...entries: string[]) {
  return render(
    <MemoryRouter initialEntries={entries} initialIndex={entries.length - 1}>
      <App />
    </MemoryRouter>,
  );
}

describe('App', () => {
  it('renderiza el inicio con la navegación inferior', async () => {
    stubApi(signedIn());

    renderAt('/');

    expect(await screen.findByRole('heading', { level: 1, name: 'Hola, Ana' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Principal' });
    const links = within(nav).getAllByRole('link');
    expect(links.map((link) => link.textContent)).toEqual([
      'Inicio',
      'Reservar',
      'Check-in',
      'Perfil',
    ]);
    expect(within(nav).getByRole('link', { name: 'Inicio' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('navega con la barra inferior', async () => {
    stubApi(signedIn());
    renderAt('/');

    await userEvent.click(await screen.findByRole('link', { name: 'Perfil' }));

    expect(screen.getByRole('heading', { level: 1, name: 'Mi perfil' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Perfil' })).toHaveAttribute('aria-current', 'page');
  });

  it.each([
    ['/reservar', 'Reservar turno'],
    ['/turno/42', 'Tu turno'],
    ['/check-in', 'Check-in'],
    ['/check-in/codigo', 'Ingresar código'],
    ['/perfil/viajes', 'Mis viajes'],
    ['/perfil/notificaciones', 'Notificaciones'],
  ])('con sesión, %s muestra "%s" sin la navegación inferior', async (path, title) => {
    stubApi(signedIn());
    renderAt(path);

    expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Principal' })).not.toBeInTheDocument();
  });

  it.each([
    ['/login', 'Bienvenido a SmartElevate'],
    ['/registro', 'Crear cuenta'],
    ['/no-existe', 'Página no encontrada'],
  ])('sin sesión, %s es pública y muestra "%s"', async (path, title) => {
    stubApi(signedOut);
    renderAt(path);

    expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument();
  });

  it.each(['/', '/reservar', '/turno/42', '/check-in/codigo', '/perfil', '/perfil/viajes'])(
    'sin sesión, %s redirige a /login',
    async (path) => {
      stubApi(signedOut);
      renderAt(path);

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Bienvenido a SmartElevate' }),
      ).toBeInTheDocument();
    },
  );

  it('"Volver" regresa a la pantalla anterior', async () => {
    stubApi(signedIn());
    renderAt('/perfil', '/perfil/viajes');

    await userEvent.click(await screen.findByRole('button', { name: 'Volver' }));

    expect(screen.getByRole('heading', { level: 1, name: 'Mi perfil' })).toBeInTheDocument();
  });

  it('"Volver" sin historial lleva a la pantalla de respaldo', async () => {
    stubApi(signedIn());
    renderAt('/reservar');

    await userEvent.click(await screen.findByRole('button', { name: 'Volver' }));

    expect(screen.getByRole('heading', { level: 1, name: 'Hola, Ana' })).toBeInTheDocument();
  });

  it('el panel admin tiene su propio menú con el Dashboard activo', async () => {
    stubApi(signedIn({ ...testUser, role: 'ADMIN' }));
    renderAt('/admin');

    const nav = await screen.findByRole('navigation', { name: 'Administración' });
    expect(within(nav).getByRole('link', { name: 'Dashboard' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(nav).getByText('Reportes')).toHaveAttribute('aria-disabled', 'true');
  });

  it('/admin sin rol ADMIN vuelve al inicio', async () => {
    stubApi(signedIn());
    renderAt('/admin');

    expect(await screen.findByRole('heading', { level: 1, name: 'Hola, Ana' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Administración' })).not.toBeInTheDocument();
  });

  it('"Cerrar sesión" limpia la sesión y vuelve a /login', async () => {
    const fetchMock = stubApi(signedIn());
    renderAt('/perfil');

    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar sesión' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Bienvenido a SmartElevate' }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', expect.anything());
  });
});
