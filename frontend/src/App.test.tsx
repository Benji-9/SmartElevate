import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { signedIn, signedOut, stubApi, testUser } from './test/stubApi';
import type { CheckInResult } from './types/pending';

const checkInResult: CheckInResult = {
  reservationId: 'r-1',
  outcome: 'ON_TIME',
  checkedInAt: '2026-09-29T17:04:30Z',
  waitDeltaSeconds: 20,
  elevatorName: 'Ascensor 2',
  coreName: 'Lima 1',
  departsAt: '2026-09-29T17:04:00Z',
  durationMinutes: 2,
};

function renderAt(...entries: string[]) {
  return render(
    <MemoryRouter initialEntries={entries} initialIndex={entries.length - 1}>
      <App />
    </MemoryRouter>,
  );
}

/*
 * La BottomNav (móvil) y el TopBar (Pantalla) se renderizan las dos con el nombre "Principal" y
 * un container query oculta una (jsdom no lo evalúa). El TopBar es el `header` que la contiene.
 */
const topBar = () =>
  screen
    .queryAllByRole('banner')
    .find((header) => within(header).queryByRole('navigation', { name: 'Principal' })) ?? null;
function bottomNav() {
  const banner = topBar();
  return (
    screen
      .queryAllByRole('navigation', { name: 'Principal' })
      .find((nav) => !banner?.contains(nav)) ?? null
  );
}

describe('App', () => {
  it('renderiza el inicio con la navegación inferior y el TopBar', async () => {
    stubApi(signedIn());

    renderAt('/');

    expect(await screen.findByRole('heading', { level: 1, name: 'Hola, Ana' })).toBeInTheDocument();
    for (const nav of [bottomNav(), within(topBar()!).getByRole('navigation')]) {
      const links = within(nav!).getAllByRole('link');
      expect(links.map((link) => link.textContent)).toEqual([
        'Inicio',
        'Reservar',
        'Check-in',
        'Perfil',
      ]);
      expect(within(nav!).getByRole('link', { name: 'Inicio' })).toHaveAttribute(
        'aria-current',
        'page',
      );
    }
  });

  it('el TopBar muestra el logo, las tabs y el nombre del usuario', async () => {
    stubApi(signedIn());
    renderAt('/');

    await screen.findByRole('heading', { level: 1, name: 'Hola, Ana' });
    const bar = topBar()!;
    expect(within(bar).getByRole('img', { name: 'SmartElevate' })).toBeInTheDocument();
    expect(within(bar).getByRole('navigation', { name: 'Principal' })).toBeInTheDocument();
    expect(await within(bar).findByText('Ana Pérez')).toBeInTheDocument();
  });

  it('navega con la barra inferior', async () => {
    stubApi(signedIn());
    renderAt('/');

    await screen.findByRole('heading', { level: 1, name: 'Hola, Ana' });
    await userEvent.click(within(bottomNav()!).getByRole('link', { name: 'Perfil' }));

    expect(screen.getByRole('heading', { level: 1, name: 'Mi perfil' })).toBeInTheDocument();
    expect(within(bottomNav()!).getByRole('link', { name: 'Perfil' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('navega con las tabs del TopBar', async () => {
    stubApi(signedIn());
    renderAt('/');

    await screen.findByRole('heading', { level: 1, name: 'Hola, Ana' });
    await userEvent.click(within(topBar()!).getByRole('link', { name: 'Reservar' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Reservar turno' }),
    ).toBeInTheDocument();
    expect(within(topBar()!).getByRole('link', { name: 'Reservar' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it.each([
    ['/reservar', 'Reservar turno', 'Reservar'],
    ['/check-in', 'Check-in', 'Check-in'],
    ['/check-in/codigo', 'Check-in', 'Check-in'],
    ['/perfil/viajes', 'Mis viajes', 'Perfil'],
    ['/perfil/notificaciones', 'Notificaciones', 'Perfil'],
  ])(
    'con sesión, %s muestra "%s" con TopBar (tab %s) y sin la navegación inferior',
    async (path, title, tab) => {
      stubApi(signedIn());
      renderAt(path);

      expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument();
      expect(bottomNav()).not.toBeInTheDocument();
      expect(within(topBar()!).getByRole('link', { name: tab })).toHaveAttribute(
        'aria-current',
        'page',
      );
    },
  );

  it('/turno/:id no lleva TopBar ni navegación inferior', async () => {
    stubApi(signedIn());
    renderAt('/turno/42');

    expect(await screen.findByRole('heading', { level: 1, name: 'Tu turno' })).toBeInTheDocument();
    expect(topBar()).not.toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Principal' })).not.toBeInTheDocument();
  });

  it('/check-in/ok (viaje registrado) no lleva TopBar ni navegación inferior', async () => {
    stubApi(signedIn());
    render(
      <MemoryRouter initialEntries={[{ pathname: '/check-in/ok', state: checkInResult }]}>
        <App />
      </MemoryRouter>,
    );

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Viaje registrado' }),
    ).toBeInTheDocument();
    expect(topBar()).not.toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Principal' })).not.toBeInTheDocument();
  });

  it.each([
    ['/login', 'Bienvenido a SmartElevate'],
    ['/registro', 'Crear cuenta'],
    ['/no-existe', 'Página no encontrada'],
  ])('sin sesión, %s es pública y muestra "%s" sin TopBar', async (path, title) => {
    stubApi(signedOut);
    renderAt(path);

    expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument();
    expect(topBar()).not.toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Principal' })).not.toBeInTheDocument();
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
