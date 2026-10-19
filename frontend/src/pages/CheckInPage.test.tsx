import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { QR_INSTRUCTION } from '../features/check-in/qrInstruction';
import { createQrReader } from '../features/check-in/qrReader';
import { signedIn, stubApi, testReservation } from '../test/stubApi';
import type { CheckIn } from '../types/api';
import { CheckInPage } from './CheckInPage';

vi.mock('../features/check-in/qrReader', () => ({ createQrReader: vi.fn() }));

const reservation = testReservation;

const result: CheckIn = {
  reservationId: 1,
  outcome: 'ON_TIME',
  reservationStatus: 'USED',
  elevator: '33',
  reservedElevator: '33',
  departureAt: '2026-09-29T17:04:00Z',
  checkedInAt: '2026-09-29T17:04:30Z',
  source: 'REAL',
};

const track = { stop: vi.fn() };

/** Cámara con permiso (o el error de `getUserMedia`) y un lector que lee `qr` en cada cuadro. */
function stubCamera({ qr = null, error }: { qr?: string | null; error?: Error } = {}) {
  track.stop.mockClear();
  const getUserMedia = vi.fn(async () => {
    if (error) throw error;
    return { getTracks: () => [track] };
  });
  Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia }, configurable: true });
  vi.mocked(createQrReader).mockResolvedValue(async () => qr);
  return getUserMedia;
}

afterEach(() => {
  delete (navigator as { mediaDevices?: unknown }).mediaDevices;
});

function ResultProbe() {
  const { state } = useLocation();
  return <p>Resultado: {JSON.stringify(state)}</p>;
}

function renderCheckIn(path = '/check-in') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/check-in" element={<CheckInPage />} />
        <Route path="/check-in/ok" element={<ResultProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

const withTurn = { ...signedIn(), 'GET /reservations/active': { body: reservation } };

describe('CheckInPage', () => {
  it('muestra el visor, las indicaciones y el turno activo', async () => {
    const getUserMedia = stubCamera();
    stubApi(withTurn);
    renderCheckIn();

    expect(screen.getByRole('heading', { level: 1, name: 'Check-in' })).toBeInTheDocument();
    expect(
      await screen.findByText('Apuntá la cámara al QR de la pantalla del ascensor'),
    ).toBeInTheDocument();
    await waitFor(() => expect(getUserMedia).toHaveBeenCalledTimes(1));
    expect(
      screen.getByText(`${QR_INSTRUCTION} El código rota: no le saques foto.`),
    ).toBeInTheDocument();
    const sheet = screen.getByRole('region', { name: 'Tu turno' });
    expect(await within(sheet).findByText('14:04 hs')).toBeInTheDocument();
    expect(within(sheet).getByText('Lima 1 · Ascensor 33 · Piso 5')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Volver' })).toBeInTheDocument();
  });

  it('sin turno activo avisa y no pide la cámara', async () => {
    const getUserMedia = stubCamera();
    stubApi({ ...signedIn(), 'GET /reservations/active': { status: 204 } });
    renderCheckIn();

    expect(await screen.findByText(/No tenés un turno activo/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Reservá un turno' })).toHaveAttribute(
      'href',
      '/reservar',
    );
    expect(screen.queryByText(/Apuntá la cámara/)).not.toBeInTheDocument();
    expect(getUserMedia).not.toHaveBeenCalled();
  });

  it('si falla la carga del turno pide la cámara igual (el servidor valida)', async () => {
    const getUserMedia = stubCamera();
    stubApi({
      ...signedIn(),
      'GET /reservations/active': { status: 500, body: { message: 'Error inesperado' } },
    });
    renderCheckIn();

    expect(await screen.findByRole('alert')).toHaveTextContent('Error inesperado');
    expect(await screen.findByText(/Apuntá la cámara/)).toBeInTheDocument();
    await waitFor(() => expect(getUserMedia).toHaveBeenCalledTimes(1));
  });

  it('al leer un QR registra el check-in y va al resultado', async () => {
    stubCamera({ qr: 'QR-TOKEN' });
    const fetchMock = stubApi({ ...withTurn, 'POST /check-ins': { body: result } });
    renderCheckIn();

    expect(await screen.findByText(`Resultado: ${JSON.stringify(result)}`)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/check-ins',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ token: 'QR-TOKEN' }) }),
    );
  });

  it.each([
    [400, 'INVALID_QR_TOKEN', 'El QR no es válido o ya venció. Escaneá el que está en pantalla.'],
    [409, 'OUTSIDE_CHECK_IN_WINDOW', 'Tu turno no está en horario de check-in.'],
    [409, 'NO_ACTIVE_RESERVATION', 'No tenés un turno para hacer check-in.'],
    [409, 'ALREADY_CHECKED_IN', 'Ya registraste el check-in de este turno.'],
  ])('si el check-in da %i %s muestra el mensaje sin salir', async (status, code, message) => {
    stubCamera({ qr: 'QR-TOKEN' });
    const fetchMock = stubApi({
      ...withTurn,
      'POST /check-ins': { status, body: { message, code } },
    });
    renderCheckIn();

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByRole('heading', { level: 1, name: 'Check-in' })).toBeInTheDocument();
    // El mismo QR no se reenvía.
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/check-ins')).toHaveLength(1);
  });

  it.each([
    ['NO_ACTIVE_RESERVATION', 'link', 'Reservá un turno', '/reservar'],
    ['ALREADY_CHECKED_IN', 'link', 'Volver al inicio', '/'],
  ])('con %s ofrece "%s" y deja de escanear', async (code, _role, name, href) => {
    const read = vi.fn(async () => `QR-${read.mock.calls.length}`);
    stubCamera();
    vi.mocked(createQrReader).mockResolvedValue(read);
    const fetchMock = stubApi({
      ...withTurn,
      'POST /check-ins': { status: 409, body: { message: 'Rechazado', code } },
    });
    renderCheckIn();

    const alert = await screen.findByRole('alert');
    expect(within(alert).getByRole('link', { name })).toHaveAttribute('href', href);
    // Llegan QR nuevos (rotan), pero con este rechazo no se reenvía ninguno.
    await waitFor(() => expect(read.mock.calls.length).toBeGreaterThan(2));
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/check-ins')).toHaveLength(1);
  });

  it('fuera de la ventana explica cuándo escanear y prueba con el QR siguiente', async () => {
    const read = vi.fn(async () => `QR-${read.mock.calls.length}`);
    stubCamera();
    vi.mocked(createQrReader).mockResolvedValue(read);
    const fetchMock = stubApi({
      ...withTurn,
      'POST /check-ins': {
        status: 409,
        body: {
          message: 'Tu turno no está en horario de check-in.',
          code: 'OUTSIDE_CHECK_IN_WINDOW',
        },
      },
    });
    renderCheckIn();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Escaneá el QR cerca de la hora de salida de tu turno.',
    );
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.filter(([url]) => url === '/api/check-ins').length,
      ).toBeGreaterThan(1),
    );
  });

  it('si se niega el permiso de cámara lo explica, sin ingreso manual', async () => {
    stubCamera({ error: new DOMException('Permiso denegado', 'NotAllowedError') });
    stubApi(withTurn);
    renderCheckIn();

    expect(
      await screen.findByRole('heading', { name: 'No tenemos permiso para usar la cámara' }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Habilitá la cámara desde la configuración/)).toBeInTheDocument();
    expect(screen.queryByText(/Apuntá la cámara/)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /manualmente/ })).not.toBeInTheDocument();
  });

  it('sin cámara en el dispositivo lo explica', async () => {
    stubApi(withTurn);
    renderCheckIn();

    expect(
      await screen.findByRole('heading', { name: 'No pudimos usar la cámara' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('sin ?demo=1 no ofrece simular el escaneo', async () => {
    stubCamera();
    stubApi(withTurn);
    renderCheckIn();

    await screen.findByText('14:04 hs');
    expect(
      screen.queryByRole('button', { name: 'Simular escaneo del QR' }),
    ).not.toBeInTheDocument();
  });

  it('con ?demo=1 "Simular escaneo del QR" registra el check-in y va al resultado', async () => {
    stubCamera();
    const fetchMock = stubApi({ ...withTurn, 'POST /check-ins': { body: result } });
    renderCheckIn('/check-in?vista=telefono&demo=1');

    await userEvent.click(screen.getByRole('button', { name: 'Simular escaneo del QR' }));

    expect(await screen.findByText(`Resultado: ${JSON.stringify(result)}`)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/check-ins',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ token: 'DEMO' }) }),
    );
  });

  it('el escaneo simulado muestra el error de la API sin salir', async () => {
    const message = 'No tenés un turno activo para hacer check-in.';
    stubApi({ ...withTurn, 'POST /check-ins': { status: 409, body: { message } } });
    renderCheckIn('/check-in?demo=1');

    await userEvent.click(screen.getByRole('button', { name: 'Simular escaneo del QR' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
  });

  it('el modo demo se conserva al navegar y ?demo=0 lo apaga', () => {
    stubApi(withTurn);
    renderCheckIn('/check-in?demo=1').unmount();

    const { unmount } = renderCheckIn('/check-in');
    expect(screen.getByRole('button', { name: 'Simular escaneo del QR' })).toBeInTheDocument();
    unmount();

    renderCheckIn('/check-in?demo=0');
    expect(
      screen.queryByRole('button', { name: 'Simular escaneo del QR' }),
    ).not.toBeInTheDocument();
  });

  it('libera la cámara al salir de la pantalla', async () => {
    stubCamera();
    stubApi(withTurn);
    const { unmount } = renderCheckIn();
    await screen.findByText('14:04 hs');

    unmount();

    await waitFor(() => expect(track.stop).toHaveBeenCalled());
  });
});
