import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { signedIn, stubApi } from '../test/stubApi';
import type { NotificationPreferences } from '../types/api';
import { NotificationsPage } from './NotificationsPage';

const prefs: NotificationPreferences = { departureReminder: false, elevatorOutOfService: true };

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
    expect(screen.getByRole('switch', { name: 'Ascensor fuera de servicio' })).toBeChecked();
    // Sin lista de espera no hay aviso de lugar liberado, y los mails no llevan datos de prioridad.
    expect(screen.getAllByRole('switch')).toHaveLength(2);
  });

  it('al activar guarda la preferencia, sin pedir permiso del navegador', async () => {
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
    const [[, init]] = putCalls(fetchMock);
    expect(JSON.parse(String(init?.body))).toEqual({ ...prefs, departureReminder: true });
  });

  it('desactivar guarda el valor nuevo', async () => {
    const fetchMock = stubApi({
      ...signedIn(),
      'GET /me/notification-preferences': { body: prefs },
      'PUT /me/notification-preferences': (init) => ({ body: JSON.parse(String(init?.body)) }),
    });
    renderPage();

    const toggle = await screen.findByRole('switch', { name: 'Ascensor fuera de servicio' });
    await userEvent.click(toggle);

    expect(toggle).not.toBeChecked();
    const [[, init]] = putCalls(fetchMock);
    expect(JSON.parse(String(init?.body))).toEqual({ ...prefs, elevatorOutOfService: false });
  });

  it('si falla el guardado, vuelve el interruptor atrás y muestra el error', async () => {
    stubApi({
      ...signedIn(),
      'GET /me/notification-preferences': { body: prefs },
      'PUT /me/notification-preferences': { status: 500, body: { message: 'Error inesperado' } },
    });
    renderPage();

    const toggle = await screen.findByRole('switch', { name: 'Recordatorio de salida' });
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
