import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';

function renderAt(...entries: string[]) {
  return render(
    <MemoryRouter initialEntries={entries} initialIndex={entries.length - 1}>
      <App />
    </MemoryRouter>,
  );
}

function stubPing(status = 'ok') {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ status }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('App', () => {
  it('renderiza el inicio con la navegación inferior y el estado de la API', async () => {
    const fetchMock = stubPing();

    renderAt('/');

    expect(screen.getByRole('heading', { level: 1, name: 'Inicio' })).toBeInTheDocument();
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
    expect(await screen.findByText('API conectada')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/ping', expect.anything());
  });

  it('muestra la API como no disponible si el ping falla', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')));

    renderAt('/');

    expect(await screen.findByText('API no disponible')).toBeInTheDocument();
  });

  it('navega con la barra inferior', async () => {
    stubPing();
    renderAt('/');

    await userEvent.click(screen.getByRole('link', { name: 'Perfil' }));

    expect(screen.getByRole('heading', { level: 1, name: 'Mi perfil' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Perfil' })).toHaveAttribute('aria-current', 'page');
  });

  it.each([
    ['/login', 'Bienvenido a SmartElevate'],
    ['/registro', 'Crear cuenta'],
    ['/reservar', 'Reservar turno'],
    ['/turno/42', 'Tu turno'],
    ['/check-in', 'Check-in'],
    ['/check-in/codigo', 'Ingresar código'],
    ['/check-in/ok', 'Viaje registrado'],
    ['/perfil/viajes', 'Mis viajes'],
    ['/perfil/notificaciones', 'Notificaciones'],
    ['/admin', 'Congestión y uso de ascensores'],
    ['/no-existe', 'Página no encontrada'],
  ])('%s muestra la pantalla "%s" sin la navegación inferior', (path, title) => {
    renderAt(path);

    expect(screen.getByRole('heading', { level: 1, name: title })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Principal' })).not.toBeInTheDocument();
  });

  it('"Volver" regresa a la pantalla anterior', async () => {
    renderAt('/perfil', '/perfil/viajes');

    await userEvent.click(screen.getByRole('button', { name: 'Volver' }));

    expect(screen.getByRole('heading', { level: 1, name: 'Mi perfil' })).toBeInTheDocument();
  });

  it('"Volver" sin historial lleva a la pantalla de respaldo', async () => {
    stubPing();
    renderAt('/reservar');

    await userEvent.click(screen.getByRole('button', { name: 'Volver' }));

    expect(screen.getByRole('heading', { level: 1, name: 'Inicio' })).toBeInTheDocument();
  });

  it('el panel admin tiene su propio menú con el Dashboard activo', () => {
    renderAt('/admin');

    const nav = screen.getByRole('navigation', { name: 'Administración' });
    expect(within(nav).getByRole('link', { name: 'Dashboard' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(nav).getByText('Reportes')).toHaveAttribute('aria-disabled', 'true');
  });
});
