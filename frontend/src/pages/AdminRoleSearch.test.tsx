import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedIn, stubApi, testUser } from '../test/stubApi';
import type { AdminUser } from '../types/api';

const carla: AdminUser = {
  id: 'a-1',
  fullName: 'Carla Díaz',
  email: 'carla@uade.edu.ar',
  legajo: '1088888',
  role: 'USER',
  declaredUserType: 'STUDENT',
  emailVerified: true,
};
const mateo: AdminUser = {
  ...carla,
  id: 'a-2',
  fullName: 'Mateo Ríos',
  email: 'mateo@uade.edu.ar',
  legajo: '1066666',
  emailVerified: false,
};
const yo: AdminUser = {
  ...carla,
  id: testUser.id,
  fullName: 'Ana Pérez',
  email: 'ana@uade.edu.ar',
  legajo: '1099999',
  role: 'ADMIN',
};

function renderPage(extra: Parameters<typeof stubApi>[0] = {}) {
  const fetchMock = stubApi({
    ...signedIn({ ...testUser, role: 'ADMIN' }),
    'GET /admin/teachers/pending': { body: [] },
    'GET /admin/priority-users': { body: [] },
    'GET /admin/users?query=carla': { body: { items: [carla, yo], nextCursor: null } },
    'GET /admin/users?query=mateo': { body: { items: [mateo], nextCursor: null } },
    ...extra,
  });
  render(
    <MemoryRouter initialEntries={['/admin/prioritarios']}>
      <App />
    </MemoryRouter>,
  );
  return fetchMock;
}

const search = async (text: string) =>
  userEvent.type(await screen.findByRole('searchbox', { name: 'Buscar usuario' }), text);

/** Abre "Gestionar usuario" de la fila y elige "Cambiar rol". */
async function openRoleSelect(name: string) {
  await userEvent.click(await screen.findByRole('button', { name: `Gestionar usuario ${name}` }));
  await userEvent.click(screen.getByRole('menuitem', { name: 'Modificar rol' }));
  return screen.findByRole('combobox', { name: `Rol de ${name}` });
}

describe('Gestión de usuarios (09b)', () => {
  it('no busca con menos de 3 caracteres y muestra la ayuda', async () => {
    const fetchMock = renderPage();

    await search('ca');
    await new Promise((r) => setTimeout(r, 400));

    expect(screen.getByText(/mínimo 3 caracteres/)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining('/admin/users'),
      expect.anything(),
    );
  });

  it('busca tras la pausa y muestra nombre, email, legajo y rol', async () => {
    const fetchMock = renderPage();

    await search('carla');

    const table = await screen.findByRole('table', { name: 'Buscar usuarios' });
    expect(within(table).getByRole('rowheader', { name: 'Carla Díaz' })).toBeInTheDocument();
    expect(within(table).getByText('carla@uade.edu.ar')).toBeInTheDocument();
    expect(within(table).getByText('1088888')).toBeInTheDocument();
    expect(within(table).getAllByText('Estudiante')).toHaveLength(2);
    expect(
      within(table).getByRole('button', { name: 'Gestionar usuario Carla Díaz' }),
    ).toBeInTheDocument();
    // Un solo pedido: el debounce agrupó las teclas.
    const searches = fetchMock.mock.calls.filter(([url]) => String(url).includes('/admin/users'));
    expect(searches).toHaveLength(1);
  });

  it('el menú ofrece modificar rol y cambiar tipo, y deja bloquear para más adelante', async () => {
    renderPage();
    await search('carla');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Gestionar usuario Carla Díaz' }),
    );

    const menu = screen.getByRole('menu', { name: 'Gestionar a Carla Díaz' });
    expect(within(menu).getByRole('menuitem', { name: 'Modificar rol' })).toBeEnabled();
    expect(within(menu).getByRole('menuitem', { name: 'Cambiar tipo' })).toBeEnabled();
    expect(within(menu).getByRole('menuitem', { name: /Bloquear cuenta/ })).toHaveAttribute(
      'aria-disabled',
      'true',
    );
  });

  it('cambiar el tipo avisa de la prioridad, manda el tipo y actualiza la fila', async () => {
    const fetchMock = renderPage({ 'PUT /admin/users/a-1/declared-type': { status: 204 } });
    await search('carla');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Gestionar usuario Carla Díaz' }),
    );
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cambiar tipo' }));
    await userEvent.selectOptions(
      await screen.findByRole('combobox', { name: 'Tipo de Carla Díaz' }),
      'TEACHER',
    );
    expect(screen.getByRole('group', { name: 'Cambiar tipo de Carla Díaz' })).toHaveTextContent(
      '¿Cambiar a Carla Díaz de Estudiante a Docente? Va a tener la prioridad de docente hasta fin del cuatrimestre.',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Sí, cambiar' }));

    const row = (await screen.findByRole('rowheader', { name: 'Carla Díaz' })).closest('tr')!;
    expect(await within(row).findByText('Docente')).toBeInTheDocument();
    const put = fetchMock.mock.calls.find(([url]) => url === '/api/admin/users/a-1/declared-type');
    expect(JSON.parse(String(put?.[1]?.body))).toEqual({ declaredUserType: 'TEACHER' });
  });

  it('de docente a estudiante avisa que se quita la prioridad', async () => {
    const profe: AdminUser = {
      ...carla,
      id: 'a-3',
      fullName: 'Rubén Sosa',
      declaredUserType: 'TEACHER',
    };
    renderPage({ 'GET /admin/users?query=carla': { body: { items: [profe], nextCursor: null } } });
    await search('carla');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Gestionar usuario Rubén Sosa' }),
    );
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cambiar tipo' }));
    await userEvent.selectOptions(
      await screen.findByRole('combobox', { name: 'Tipo de Rubén Sosa' }),
      'STUDENT',
    );

    expect(screen.getByRole('group', { name: 'Cambiar tipo de Rubén Sosa' })).toHaveTextContent(
      'Pierde la prioridad de docente si la tenía vigente.',
    );
  });

  it('con el email sin verificar no deja cambiar el tipo y lo explica', async () => {
    renderPage();
    await search('mateo');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Gestionar usuario Mateo Ríos' }),
    );

    expect(screen.getByRole('menuitem', { name: /Cambiar tipo/ })).toBeDisabled();
    expect(screen.getByText('Email sin verificar')).toBeInTheDocument();
  });

  it('si cambiar el tipo falla muestra el error y deja el tipo como estaba', async () => {
    renderPage({
      'PUT /admin/users/a-1/declared-type': { status: 409, body: { message: 'Falta el cierre' } },
    });
    await search('carla');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Gestionar usuario Carla Díaz' }),
    );
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cambiar tipo' }));
    await userEvent.selectOptions(
      await screen.findByRole('combobox', { name: 'Tipo de Carla Díaz' }),
      'TEACHER',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Sí, cambiar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Falta el cierre');
    const row = screen.getByRole('rowheader', { name: 'Carla Díaz' }).closest('tr')!;
    expect(within(row).getByText('Estudiante')).toBeInTheDocument();
  });

  it('la cuenta propia no ofrece cambiar el rol', async () => {
    renderPage();

    await search('carla');

    expect(await screen.findByText('Es tu cuenta')).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Rol de Ana Pérez' })).not.toBeInTheDocument();
  });

  it('el selector de rol solo ofrece Usuario y Administrador', async () => {
    renderPage();
    await search('carla');

    const select = await openRoleSelect('Carla Díaz');

    expect(
      within(select)
        .getAllByRole('option')
        .map((o) => o.textContent),
    ).toEqual(['Usuario', 'Administrador']);
  });

  it('cambiar el rol pide confirmación y se puede cancelar', async () => {
    const fetchMock = renderPage();
    await search('carla');

    await userEvent.selectOptions(await openRoleSelect('Carla Díaz'), 'ADMIN');
    expect(screen.getByRole('group', { name: 'Cambiar rol de Carla Díaz' })).toHaveTextContent(
      '¿Cambiar a Carla Díaz de Usuario a Administrador?',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.getByRole('combobox', { name: 'Rol de Carla Díaz' })).toHaveValue('USER');
    expect(fetchMock).not.toHaveBeenCalledWith('/api/admin/users/a-1/role', expect.anything());
  });

  it('confirmar manda el rol y actualiza la fila', async () => {
    const fetchMock = renderPage({ 'PUT /admin/users/a-1/role': { status: 204 } });
    await search('carla');

    await userEvent.selectOptions(await openRoleSelect('Carla Díaz'), 'ADMIN');
    await userEvent.click(screen.getByRole('button', { name: 'Sí, cambiar' }));

    const row = (await screen.findByRole('rowheader', { name: 'Carla Díaz' })).closest('tr')!;
    expect(await within(row).findByText('Administrador')).toBeInTheDocument();
    const put = fetchMock.mock.calls.find(([url]) => url === '/api/admin/users/a-1/role');
    expect(JSON.parse(String(put?.[1]?.body))).toEqual({ role: 'ADMIN' });
  });

  it('si el cambio falla muestra el error y deja el rol como estaba', async () => {
    renderPage({
      'PUT /admin/users/a-1/role': { status: 409, body: { message: 'No se puede' } },
    });
    await search('carla');

    await userEvent.selectOptions(await openRoleSelect('Carla Díaz'), 'ADMIN');
    await userEvent.click(screen.getByRole('button', { name: 'Sí, cambiar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se puede');
    const row = screen.getByRole('rowheader', { name: 'Carla Díaz' }).closest('tr')!;
    expect(within(row).getByText('Usuario')).toBeInTheDocument();
  });

  it('sin resultados lo avisa', async () => {
    renderPage({ 'GET /admin/users?query=zzz': { body: { items: [], nextCursor: null } } });

    await search('zzz');

    expect(await screen.findByText('No encontramos usuarios para «zzz».')).toBeInTheDocument();
  });

  it('error de búsqueda con Reintentar', async () => {
    renderPage({
      'GET /admin/users?query=carla': { status: 500, body: { message: 'Algo salió mal' } },
    });

    await search('carla');

    expect(await screen.findByText('Algo salió mal')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('"Ver más resultados" agrega la página siguiente con el cursor', async () => {
    const lucia: AdminUser = { ...carla, id: 'a-2', fullName: 'Lucía Fernández' };
    renderPage({
      'GET /admin/users?query=carla': { body: { items: [carla], nextCursor: 'c-1' } },
      'GET /admin/users?query=carla&cursor=c-1': { body: { items: [lucia], nextCursor: null } },
    });
    await search('carla');

    await userEvent.click(await screen.findByRole('button', { name: 'Ver más resultados' }));

    expect(await screen.findByRole('rowheader', { name: 'Lucía Fernández' })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Carla Díaz' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ver más resultados' })).not.toBeInTheDocument();
  });
});
