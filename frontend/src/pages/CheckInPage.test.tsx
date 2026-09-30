import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createQrReader } from '../features/check-in/qrReader';
import { signedIn, stubApi } from '../test/stubApi';
import type { CheckInResult, Reservation } from '../types/pending';
import { CheckInPage } from './CheckInPage';

vi.mock('../features/check-in/qrReader', () => ({ createQrReader: vi.fn() }));

const reservation: Reservation = {
  id: 'r-1',
  status: 'ACTIVE',
  departure: {
    id: 'd-1',
    coreId: 'c-1',
    departsAt: '2026-09-29T17:04:00Z',
    durationMinutes: 2,
    capacity: 10,
    occupied: 7,
  },
  core: { id: 'c-1', name: 'L1', buildingName: 'Lima', floors: [0, 10], hall: 'Hall Lima, PB' },
  originFloor: 0,
  destinationFloor: 5,
  cancelCountsAsNoShow: false,
};

const result: CheckInResult = {
  reservationId: 'r-1',
  outcome: 'ON_TIME',
  checkedInAt: '2026-09-29T17:04:30Z',
  waitDeltaSeconds: 30,
  elevatorName: 'Ascensor 1',
  coreName: 'L1',
  departsAt: '2026-09-29T17:04:00Z',
  durationMinutes: 2,
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
        <Route path="/check-in/codigo" element={<CheckInPage manual />} />
        <Route path="/check-in/ok" element={<ResultProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

const withTurn = { ...signedIn(), 'GET /reservations/active': { body: reservation } };

describe('CheckInPage', () => {
  it('muestra el visor, las indicaciones y el turno activo', async () => {
    stubCamera();
    stubApi(withTurn);
    renderCheckIn();

    expect(screen.getByRole('heading', { level: 1, name: 'Check-in' })).toBeInTheDocument();
    expect(
      screen.getByText('Apuntá la cámara al QR de la pantalla del ascensor'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('El código rota: escanealo desde dentro de la cabina'),
    ).toBeInTheDocument();
    const sheet = screen.getByRole('region', { name: 'Tu turno' });
    expect(await within(sheet).findByText('14:04 – 14:06')).toBeInTheDocument();
    expect(within(sheet).getByText('Ascensor L1')).toBeInTheDocument();
    expect(within(sheet).getByText('Hall Lima, PB')).toBeInTheDocument();
  });

  it('avisa si no hay turno activo', async () => {
    stubCamera();
    stubApi({ ...signedIn(), 'GET /reservations/active': { body: null } });
    renderCheckIn();

    expect(await screen.findByText(/No tenés un turno activo/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Reservá un turno' })).toHaveAttribute(
      'href',
      '/reservar',
    );
  });

  it('al leer un QR registra el check-in y va al resultado', async () => {
    stubCamera({ qr: 'QR-TOKEN' });
    const fetchMock = stubApi({ ...withTurn, 'POST /check-ins': { body: result } });
    renderCheckIn();

    expect(await screen.findByText(`Resultado: ${JSON.stringify(result)}`)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/check-ins',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ code: 'QR-TOKEN' }) }),
    );
  });

  it.each([
    [400, 'El código venció. Escanealo de nuevo desde la cabina.'],
    [409, 'No tenés un turno activo para hacer check-in.'],
    [409, 'Ya registraste el check-in de este turno.'],
  ])('si el check-in da %i muestra "%s" sin salir', async (status, message) => {
    stubCamera({ qr: 'QR-TOKEN' });
    const fetchMock = stubApi({ ...withTurn, 'POST /check-ins': { status, body: { message } } });
    renderCheckIn();

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByRole('heading', { level: 1, name: 'Check-in' })).toBeInTheDocument();
    // El mismo QR no se reenvía.
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/check-ins')).toHaveLength(1);
  });

  it('si se niega el permiso de cámara lo explica y ofrece el código manual', async () => {
    stubCamera({ error: new DOMException('Permiso denegado', 'NotAllowedError') });
    stubApi(withTurn);
    renderCheckIn();

    expect(
      await screen.findByRole('heading', { name: 'No tenemos permiso para usar la cámara' }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Apuntá la cámara/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('link', { name: 'Ingresar código manualmente' }));

    expect(screen.getByRole('dialog', { name: 'Ingresá el código' })).toBeInTheDocument();
  });

  it('sin cámara en el dispositivo también ofrece el código manual', async () => {
    stubApi(withTurn);
    renderCheckIn();

    expect(screen.getByRole('heading', { name: 'No pudimos usar la cámara' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ingresar código manualmente' })).toBeInTheDocument();
  });

  it('con el código manual registra el check-in y va al resultado', async () => {
    stubCamera();
    const fetchMock = stubApi({ ...withTurn, 'POST /check-ins': { body: result } });
    renderCheckIn('/check-in/codigo');

    const dialog = screen.getByRole('dialog', { name: 'Ingresá el código' });
    const input = within(dialog).getByLabelText('Código del ascensor');
    await userEvent.type(input, ' AB12 ');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar check-in' }));

    expect(await screen.findByText(`Resultado: ${JSON.stringify(result)}`)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/check-ins',
      expect.objectContaining({ body: JSON.stringify({ code: 'AB12' }) }),
    );
  });

  it('muestra el error del código manual en el sheet y "Volver a escanear" lo cierra', async () => {
    stubCamera();
    stubApi({
      ...withTurn,
      'POST /check-ins': { status: 400, body: { message: 'Código inválido o vencido.' } },
    });
    renderCheckIn('/check-in/codigo');

    const dialog = screen.getByRole('dialog', { name: 'Ingresá el código' });
    await userEvent.type(within(dialog).getByLabelText('Código del ascensor'), 'VENCIDO');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar check-in' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Código inválido o vencido.',
    );

    await userEvent.click(within(dialog).getByRole('button', { name: 'Volver a escanear' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('sin ?demo=1 no ofrece simular el escaneo', async () => {
    stubCamera();
    stubApi(withTurn);
    renderCheckIn();

    await screen.findByText('14:04 – 14:06');
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
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ code: 'DEMO' }) }),
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
    await screen.findByText('14:04 – 14:06');

    unmount();

    expect(track.stop).toHaveBeenCalled();
  });
});
