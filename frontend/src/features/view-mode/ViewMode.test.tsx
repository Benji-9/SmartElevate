import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../../App';
import { signedIn, signedOut, stubApi, testUser } from '../../test/stubApi';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

const toggle = () => screen.queryByRole('radiogroup', { name: 'Vista de la aplicación' });
const option = (name: string) => screen.getByRole('radio', { name });
// La barra de estado es decorativa (aria-hidden): solo existe dentro del marco.
const statusBar = () => screen.queryByText('9:41');

async function renderLogin(path = '/login') {
  stubApi(signedOut);
  renderAt(path);
  await screen.findByRole('heading', { level: 1, name: 'Te damos la bienvenida a SmartElevate' });
}

describe('Modos de vista', () => {
  it('con la ventana de 1024 px o más muestra el selector en Pantalla por defecto', async () => {
    await renderLogin();

    expect(toggle()).toBeInTheDocument();
    expect(option('Pantalla')).toHaveAttribute('aria-checked', 'true');
    expect(option('Teléfono')).toHaveAttribute('aria-checked', 'false');
    expect(statusBar()).not.toBeInTheDocument();
  });

  it('al elegir Teléfono dibuja el marco con la barra de estado y lo guarda', async () => {
    await renderLogin();

    await userEvent.click(option('Teléfono'));

    expect(option('Teléfono')).toHaveAttribute('aria-checked', 'true');
    expect(statusBar()).toBeInTheDocument();
    expect(localStorage.getItem('se:viewMode')).toBe('telefono');
  });

  it('usa el modo guardado al recargar', async () => {
    localStorage.setItem('se:viewMode', 'telefono');
    await renderLogin();

    expect(option('Teléfono')).toHaveAttribute('aria-checked', 'true');
  });

  it('?vista= le gana al modo guardado', async () => {
    localStorage.setItem('se:viewMode', 'pantalla');
    await renderLogin('/login?vista=telefono');

    expect(option('Teléfono')).toHaveAttribute('aria-checked', 'true');
    expect(statusBar()).toBeInTheDocument();
  });

  it('funciona aunque localStorage tire error', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('Bloqueado', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Bloqueado', 'SecurityError');
    });
    await renderLogin();

    expect(option('Pantalla')).toHaveAttribute('aria-checked', 'true');
    await userEvent.click(option('Teléfono'));
    expect(option('Teléfono')).toHaveAttribute('aria-checked', 'true');
  });

  it('con las flechas cambia la opción y mueve el foco (tabindex itinerante)', async () => {
    await renderLogin();
    option('Pantalla').focus();

    await userEvent.keyboard('{ArrowRight}');
    expect(option('Teléfono')).toHaveAttribute('aria-checked', 'true');
    expect(option('Teléfono')).toHaveFocus();
    expect(option('Pantalla')).toHaveAttribute('tabindex', '-1');

    await userEvent.keyboard('{ArrowLeft}');
    expect(option('Pantalla')).toHaveAttribute('aria-checked', 'true');
    expect(option('Pantalla')).toHaveFocus();
  });

  it('cambiar de modo no reinicia la pantalla ni borra lo escrito', async () => {
    await renderLogin();
    await userEvent.type(screen.getByLabelText('Email institucional'), 'ana.perez');

    await userEvent.click(option('Teléfono'));
    await userEvent.click(option('Pantalla'));

    expect(screen.getByLabelText('Email institucional')).toHaveValue('ana.perez');
    expect(
      screen.getByRole('heading', { level: 1, name: 'Te damos la bienvenida a SmartElevate' }),
    ).toBeInTheDocument();
  });

  it('con la ventana de menos de 1024 px no hay selector ni marco', async () => {
    vi.stubGlobal('innerWidth', 800);
    localStorage.setItem('se:viewMode', 'telefono');
    await renderLogin();

    expect(toggle()).not.toBeInTheDocument();
    expect(statusBar()).not.toBeInTheDocument();

    vi.stubGlobal('innerWidth', 1280);
    act(() => window.dispatchEvent(new Event('resize')));
    expect(toggle()).toBeInTheDocument();
    expect(statusBar()).toBeInTheDocument();
  });

  it('en /admin no hay selector ni marco, y el modo elegido se conserva', async () => {
    localStorage.setItem('se:viewMode', 'telefono');
    stubApi(signedIn({ ...testUser, role: 'ADMIN' }));
    renderAt('/admin');

    await screen.findByRole('navigation', { name: 'Administración' });
    expect(toggle()).not.toBeInTheDocument();
    expect(statusBar()).not.toBeInTheDocument();
    expect(localStorage.getItem('se:viewMode')).toBe('telefono');
  });
});
