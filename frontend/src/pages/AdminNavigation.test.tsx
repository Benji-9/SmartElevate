import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedIn, stubApi, testUser } from '../test/stubApi';
import type { AdminElevator, User } from '../types/api';

function renderAt(path: string, user: User, extra = {}) {
  const fetchMock = stubApi({
    ...signedIn(user),
    'GET /admin/elevators': { body: [] },
    'GET /admin/kiosks': { body: { kiosks: [], simulatorEnabled: false } },
    ...extra,
  });
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
  return fetchMock;
}

describe('navegación entre la app y el panel', () => {
  it('un ADMIN ve el link al panel en Perfil', async () => {
    renderAt('/perfil', { ...testUser, role: 'ADMIN' });
    expect(await screen.findByRole('link', { name: /Panel de administración/ })).toHaveAttribute(
      'href',
      '/admin',
    );
  });

  it('un usuario común no ve el link al panel', async () => {
    renderAt('/perfil', testUser);
    await screen.findByRole('heading', { name: 'Ana Pérez' });
    expect(screen.queryByRole('link', { name: /Panel/ })).not.toBeInTheDocument();
  });

  it('el panel tiene "Volver a la app" y "Cerrar sesión"', async () => {
    const fetchMock = renderAt('/admin/ascensores', { ...testUser, role: 'ADMIN' });
    const back = await screen.findByRole('link', { name: 'Volver a la app' });
    expect(back).toHaveAttribute('href', '/');
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', expect.anything());
  });

  it('sin rol ADMIN, /admin avisa en lugar de redirigir en silencio', async () => {
    renderAt('/admin', testUser);
    expect(
      await screen.findByRole('heading', { name: 'No tenés acceso a esta sección' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute('href', '/');
  });
});

describe('Perfil: estado de la prioridad', () => {
  it('docente declarado sin aprobar: pendiente de validación', async () => {
    renderAt('/perfil', { ...testUser, declaredUserType: 'TEACHER' });
    expect(
      await screen.findByText('Prioridad de docente: pendiente de validación'),
    ).toBeInTheDocument();
  });

  it('docente aprobado: vigente', async () => {
    renderAt('/perfil', { ...testUser, declaredUserType: 'TEACHER', priority: 'TEACHER' });
    expect(await screen.findByText('Prioridad de docente: vigente')).toBeInTheDocument();
  });

  it('estudiante: sin estado de prioridad', async () => {
    renderAt('/perfil', testUser);
    await screen.findByRole('heading', { name: 'Ana Pérez' });
    expect(screen.queryByText(/Prioridad de docente/)).not.toBeInTheDocument();
  });
});

describe('Ascensores por núcleo', () => {
  const el = (code: string, coreName: string, o: Partial<AdminElevator> = {}): AdminElevator => ({
    code,
    coreName,
    building: 'LIMA',
    usage: 'COMMON',
    active: true,
    upcomingReservations: 0,
    ...o,
  });

  it('agrupa por núcleo, marca dedicados y fuera de servicio', async () => {
    renderAt(
      '/admin/ascensores',
      { ...testUser, role: 'ADMIN' },
      {
        'GET /admin/elevators': {
          body: [
            el('1', 'Lima 1'),
            el('5', 'Lima 3', { usage: 'TEACHERS' }),
            el('6', 'Lima 3', { usage: 'REDUCED_MOBILITY' }),
            el('2', 'Lima 2', { active: false }),
            el('3', 'Lima 3'),
          ],
        },
      },
    );
    const lima3 = await screen.findByRole('region', { name: 'Lima 3' });
    expect(within(lima3).getAllByRole('article')).toHaveLength(3);
    expect(within(lima3).getByText('Dedicado a docentes')).toBeInTheDocument();
    expect(within(lima3).getByText('Dedicado a movilidad reducida')).toBeInTheDocument();
    const lima2 = screen.getByRole('region', { name: 'Lima 2' });
    expect(within(lima2).getByText('Fuera de servicio')).toBeInTheDocument();
  });
});
