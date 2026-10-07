import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { signedIn, stubApi } from '../test/stubApi';
import type { NotificationPreferences } from '../types/pending';
import { NotificationsPage } from './NotificationsPage';

const prefs: NotificationPreferences = {
  departureReminder: false,
  spotReleased: false,
  delayCancellation: false,
  priorityAccess: true,
};

function stubNotification(permission: NotificationPermission, answer = permission) {
  const requestPermission = vi.fn(async () => answer);
  vi.stubGlobal('Notification', { permission, requestPermission });
  return requestPermission;
}

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/perfil/notificaciones']}>
      <NotificationsPage />
    </MemoryRouter>,
  );
}

const putCalls = (fetchMock: ReturnType<typeof stubApi>) =>
  fetchMock.mock.calls.filter(([, init]) => init?.method === 'PUT');

describe('NotificationsPage', () => {
  it('muestra las preferencias como interruptores', async () => {
    stubApi({ ...signedIn(), 'GET /me/notification-preferences': { body: prefs } });
    renderPage();

    expect(screen.getByRole('heading', { level: 1, name: 'Notificaciones' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Cargando');
    expect(await screen.findByRole('switch', { name: 'Recordatorio de salida' })).not.toBeChecked();
    // Sin lista de espera en la UI, no se ofrece el aviso de lugar liberado (#157).
    expect(screen.queryByRole('switch', { name: 'Lugar liberado' })).not.toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Cancelación por demora' })).not.toBeChecked();
    expect(screen.getByRole('switch', { name: 'Acceso prioritario' })).toBeChecked();
  });

  it('al activar pide permiso y guarda la preferencia', async () => {
    const requestPermission = stubNotification('default', 'granted');
    const fetchMock = stubApi({
      ...signedIn(),
      'GET /me/notification-preferences': { body: prefs },
      'PUT /me/notification-preferences': (init) => ({ body: JSON.parse(String(init?.body)) }),
    });
    renderPage();

    const toggle = await screen.findByRole('switch', { name: 'Recordatorio de salida' });
    await userEvent.click(toggle);

    expect(toggle).toBeChecked();
    expect(await screen.findByRole('status')).toHaveTextContent('Guardamos tu preferencia.');
    expect(requestPermission).toHaveBeenCalledOnce();
    const [[, init]] = putCalls(fetchMock);
    expect(JSON.parse(String(init?.body))).toEqual({ ...prefs, departureReminder: true });
  });

  it('si el permiso está denegado, no activa y explica cómo habilitarlo', async () => {
    const requestPermission = stubNotification('default', 'denied');
    const fetchMock = stubApi({
      ...signedIn(),
      'GET /me/notification-preferences': { body: prefs },
    });
    renderPage();

    const toggle = await screen.findByRole('switch', { name: 'Recordatorio de salida' });
    await userEvent.click(toggle);

    expect(requestPermission).toHaveBeenCalledOnce();
    expect(toggle).not.toBeChecked();
    expect(screen.getByRole('alert')).toHaveTextContent(/configuración del sitio/);
    expect(putCalls(fetchMock)).toHaveLength(0);
  });

  it('si el navegador no admite notificaciones, no activa y lo avisa', async () => {
    vi.stubGlobal('Notification', undefined);
    stubApi({ ...signedIn(), 'GET /me/notification-preferences': { body: prefs } });
    renderPage();

    const toggle = await screen.findByRole('switch', { name: 'Recordatorio de salida' });
    await userEvent.click(toggle);

    expect(toggle).not.toBeChecked();
    expect(screen.getByRole('alert')).toHaveTextContent(/no admite notificaciones/);
  });

  it('desactivar guarda sin pedir permiso', async () => {
    const requestPermission = stubNotification('default');
    const fetchMock = stubApi({
      ...signedIn(),
      'GET /me/notification-preferences': { body: prefs },
      'PUT /me/notification-preferences': (init) => ({ body: JSON.parse(String(init?.body)) }),
    });
    renderPage();

    const toggle = await screen.findByRole('switch', { name: 'Acceso prioritario' });
    await userEvent.click(toggle);

    expect(toggle).not.toBeChecked();
    expect(requestPermission).not.toHaveBeenCalled();
    const [[, init]] = putCalls(fetchMock);
    expect(JSON.parse(String(init?.body))).toEqual({ ...prefs, priorityAccess: false });
  });

  it('si falla el guardado, vuelve el interruptor atrás y muestra el error', async () => {
    stubNotification('granted');
    stubApi({
      ...signedIn(),
      'GET /me/notification-preferences': { body: prefs },
      'PUT /me/notification-preferences': { status: 500, body: { message: 'Error inesperado' } },
    });
    renderPage();

    const toggle = await screen.findByRole('switch', { name: 'Cancelación por demora' });
    await userEvent.click(toggle);

    expect(await screen.findByRole('alert')).toHaveTextContent('Error inesperado');
    expect(toggle).not.toBeChecked();
    expect(screen.getByRole('status')).toBeEmptyDOMElement();
  });

  it('muestra el error si no se pudieron cargar', async () => {
    stubApi({
      ...signedIn(),
      'GET /me/notification-preferences': { status: 500, body: { message: 'Error inesperado' } },
    });
    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('Error inesperado');
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
