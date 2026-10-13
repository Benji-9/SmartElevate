import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedIn, stubApi, testUser } from '../test/stubApi';
import type { PendingTeacher, PriorityUser } from '../types/pending';

const marta: PendingTeacher = {
  id: 't-1',
  fullName: 'Marta Gómez',
  email: 'marta@uade.edu.ar',
  legajo: '2001234',
  registeredAt: '2026-10-08T14:20:00Z',
};
const carla: PriorityUser = {
  id: 'p-1',
  fullName: 'Carla Díaz',
  email: 'carla@uade.edu.ar',
  legajo: '1088888',
  category: 'REDUCED_MOBILITY',
};

/** Servidor simulado con estado: la lista cambia después de aprobar / rechazar / revocar. */
function renderPage(
  { pending = [marta], priority = [carla] } = {},
  extra: Parameters<typeof stubApi>[0] = {},
) {
  let pendingList = pending;
  let priorityList = priority;
  const fetchMock = stubApi({
    ...signedIn({ ...testUser, role: 'ADMIN' }),
    'GET /admin/teachers/pending': () => ({ body: pendingList }),
    'GET /admin/priority-users': () => ({ body: priorityList }),
    'POST /admin/teachers/t-1/approve': () => {
      pendingList = [];
      priorityList = [...priorityList, { ...marta, category: 'TEACHER' }];
      return { status: 204 };
    },
    'POST /admin/teachers/t-1/reject': () => {
      pendingList = [];
      return { status: 204 };
    },
    'DELETE /admin/priority-users/p-1': () => {
      priorityList = [];
      return { status: 204 };
    },
    ...extra,
  });
  render(
    <MemoryRouter initialEntries={['/admin/prioritarios']}>
      <App />
    </MemoryRouter>,
  );
  return fetchMock;
}

describe('AdminPriorityPage', () => {
  it('lista los docentes a validar y los usuarios con prioridad', async () => {
    renderPage();

    const queue = await screen.findByRole('table', { name: 'Docentes a validar' });
    expect(within(queue).getByRole('rowheader', { name: 'Marta Gómez' })).toBeInTheDocument();
    expect(within(queue).getByText('marta@uade.edu.ar')).toBeInTheDocument();
    expect(within(queue).getByText('2001234')).toBeInTheDocument();
    expect(within(queue).getByText('8/10/2026')).toBeInTheDocument();
    const priority = await screen.findByRole('table', { name: 'Usuarios con prioridad' });
    expect(within(priority).getByText('Movilidad reducida')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Usuarios prioritarios' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('aprobar saca al docente de la cola y lo suma a los prioritarios', async () => {
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar a Marta Gómez' }));

    expect(await screen.findByText('No hay docentes para validar.')).toBeInTheDocument();
    const priority = await screen.findByRole('table', { name: 'Usuarios con prioridad' });
    expect(await within(priority).findByText('Docente')).toBeInTheDocument();
  });

  it('rechazar pide confirmación y se puede cancelar', async () => {
    const fetchMock = renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Rechazar a Marta Gómez' }));
    expect(screen.getByRole('group', { name: '¿Rechazar a Marta Gómez?' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.getByRole('button', { name: 'Rechazar a Marta Gómez' })).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith('/api/admin/teachers/t-1/reject', expect.anything());
  });

  it('confirmar el rechazo actualiza la cola', async () => {
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Rechazar a Marta Gómez' }));
    await userEvent.click(screen.getByRole('button', { name: 'Sí, rechazar' }));

    expect(await screen.findByText('No hay docentes para validar.')).toBeInTheDocument();
  });

  it('revocar confirma y deja la lista vacía', async () => {
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Revocar a Carla Díaz' }));
    await userEvent.click(screen.getByRole('button', { name: 'Sí, revocar' }));

    expect(await screen.findByText('Todavía no hay usuarios con prioridad.')).toBeInTheDocument();
  });

  it('muestra el error del servidor si falla una acción y deja la fila', async () => {
    renderPage(
      {},
      { 'POST /admin/teachers/t-1/approve': { status: 409, body: { message: 'Ya fue resuelto' } } },
    );

    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar a Marta Gómez' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya fue resuelto');
    expect(screen.getByRole('button', { name: 'Aprobar a Marta Gómez' })).toBeInTheDocument();
  });

  it('muestra el error de carga con Reintentar', async () => {
    renderPage(
      {},
      { 'GET /admin/teachers/pending': { status: 500, body: { message: 'Algo salió mal' } } },
    );

    expect(await screen.findByText('Algo salió mal')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('muestra los estados vacíos', async () => {
    renderPage({ pending: [], priority: [] });

    expect(await screen.findByText('No hay docentes para validar.')).toBeInTheDocument();
    expect(await screen.findByText('Todavía no hay usuarios con prioridad.')).toBeInTheDocument();
  });
});
