import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';
import { signedIn, stubApi } from '../test/stubApi';
import type { Reservation } from '../types/pending';
import { TurnPage } from './TurnPage';

const reservation: Reservation = {
  id: 'r-1',
  status: 'ACTIVE',
  departure: {
    id: 'd-1',
    coreId: 'c-1',
    departsAt: '2026-09-29T17:04:00Z',
    durationMinutes: 2,
    capacity: 12,
    occupied: 7,
  },
  core: {
    id: 'c-1',
    name: 'L1',
    buildingName: 'Lima',
    floors: [0, 1, 2, 5, 10],
    hall: 'Hall Lima, planta baja',
  },
  originFloor: 0,
  destinationFloor: 5,
  cancelCountsAsNoShow: false,
};

function HomeProbe() {
  const { state } = useLocation();
  return <p>Inicio: {(state as { notice?: string } | null)?.notice}</p>;
}

function renderTurn(id = 'r-1') {
  render(
    <MemoryRouter initialEntries={[`/turno/${id}`]}>
      <Routes>
        <Route path="/" element={<HomeProbe />} />
        <Route path="/turno/:id" element={<TurnPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function openCancelDialog() {
  await userEvent.click(await screen.findByRole('button', { name: 'Cancelar turno' }));
  return screen.getByRole('dialog', { name: '¿Cancelar el turno?' });
}

describe('TurnPage', () => {
  it('muestra el turno confirmado con su detalle', async () => {
    stubApi({ ...signedIn(), 'GET /reservations/r-1': { body: reservation } });
    renderTurn();

    expect(screen.getByRole('status')).toHaveTextContent('Cargando');
    expect(
      await screen.findByRole('heading', { level: 1, name: '¡Turno confirmado!' }),
    ).toBeInTheDocument();
    // Pantalla de confirmación: sin header ni botón volver.
    expect(screen.queryByRole('heading', { name: 'Tu turno' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Volver' })).not.toBeInTheDocument();
    expect(screen.getByText('Esperá en Hall Lima, planta baja.')).toBeInTheDocument();
    expect(screen.getByText('Lima')).toBeInTheDocument();
    expect(screen.getByText(/pisos PB a 10/)).toBeInTheDocument();
    expect(screen.getByText('14:04 – 14:06')).toBeInTheDocument();
    expect(screen.getByText('7 / 12 personas')).toBeInTheDocument();
    expect(screen.getByText(/escaneá el QR que aparece en la pantalla/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a check-in' })).toHaveAttribute(
      'href',
      '/check-in',
    );
  });

  it('cancela el turno y vuelve al inicio con un aviso', async () => {
    const fetchMock = stubApi({
      ...signedIn(),
      'GET /reservations/r-1': { body: reservation },
      'DELETE /reservations/r-1': { status: 204 },
    });
    renderTurn();

    const dialog = await openCancelDialog();
    expect(dialog).toHaveAccessibleDescription(/liberar tu lugar/);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Sí, cancelar' }));

    expect(await screen.findByText('Inicio: Cancelaste tu turno.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/reservations/r-1',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('"Volver" cierra el diálogo sin cancelar', async () => {
    const fetchMock = stubApi({ ...signedIn(), 'GET /reservations/r-1': { body: reservation } });
    renderTurn();

    const dialog = await openCancelDialog();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Volver' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/reservations/r-1',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('avisa antes de confirmar si la cancelación cuenta como falta', async () => {
    stubApi({
      ...signedIn(),
      'GET /reservations/r-1': { body: { ...reservation, cancelCountsAsNoShow: true } },
    });
    renderTurn();

    const dialog = await openCancelDialog();

    expect(dialog).toHaveAccessibleDescription(/cuenta como falta/);
  });

  it('muestra el error si no se pudo cancelar', async () => {
    stubApi({
      ...signedIn(),
      'GET /reservations/r-1': { body: reservation },
      'DELETE /reservations/r-1': { status: 409, body: { message: 'La salida ya partió' } },
    });
    renderTurn();

    const dialog = await openCancelDialog();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Sí, cancelar' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('La salida ya partió');
  });

  it('si el turno no existe o no es del usuario, muestra el estado de error', async () => {
    stubApi(signedIn());
    renderTurn('42');

    expect(
      await screen.findByRole('heading', { level: 2, name: 'No encontramos este turno' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Tu turno' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Volver' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir al inicio' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: 'Reservar un turno' })).toHaveAttribute(
      'href',
      '/reservar',
    );
  });

  it('muestra otros errores como alerta', async () => {
    stubApi({
      ...signedIn(),
      'GET /reservations/r-1': { status: 500, body: { message: 'Error inesperado' } },
    });
    renderTurn();

    expect(await screen.findByRole('alert')).toHaveTextContent('Error inesperado');
  });
});
