import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';
import { QR_INSTRUCTION } from '../features/check-in/qrInstruction';
import { signedIn, stubApi, testReservation } from '../test/stubApi';
import { TurnPage } from './TurnPage';

const reservation = testReservation;

function HomeProbe() {
  const { state } = useLocation();
  return <p>Inicio: {(state as { notice?: string } | null)?.notice}</p>;
}

function renderTurn(id = '55', justReserved = true) {
  render(
    <MemoryRouter
      initialEntries={[{ pathname: `/turno/${id}`, state: justReserved ? { justReserved } : null }]}
    >
      <Routes>
        <Route path="/" element={<HomeProbe />} />
        <Route path="/turno/:id" element={<TurnPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function openCancelDialog(name = '¿Cancelar el turno?') {
  await userEvent.click(await screen.findByRole('button', { name: 'Cancelar turno' }));
  return screen.getByRole('dialog', { name });
}

describe('TurnPage', () => {
  it('muestra el turno confirmado con su detalle', async () => {
    stubApi({ ...signedIn(), 'GET /reservations/55': { body: reservation } });
    renderTurn();

    expect(screen.getByRole('status')).toHaveTextContent('Cargando');
    expect(
      await screen.findByRole('heading', { level: 1, name: '¡Turno confirmado!' }),
    ).toBeInTheDocument();
    // Pantalla de confirmación: sin header ni botón volver.
    expect(screen.queryByRole('heading', { name: 'Tu turno' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Volver' })).not.toBeInTheDocument();
    expect(screen.getByText('Te esperamos en el hall de Lima 1.')).toBeInTheDocument();
    expect(screen.getByText('Lima')).toBeInTheDocument();
    expect(screen.getByText(/· Lima 1/)).toBeInTheDocument();
    expect(screen.getByText('14:04 hs')).toBeInTheDocument();
    expect(screen.getByText('Elevy te guía')).toBeInTheDocument();
    expect(screen.getByText(QR_INSTRUCTION)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a check-in' })).toHaveAttribute(
      'href',
      '/check-in',
    );
  });

  it('al reabrir un turno existente no festeja y ofrece volver', async () => {
    stubApi({ ...signedIn(), 'GET /reservations/55': { body: reservation } });
    renderTurn('55', false);

    expect(await screen.findByRole('heading', { level: 1, name: 'Tu turno' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Volver' })).toBeInTheDocument();
    expect(screen.queryByText('¡Turno confirmado!')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a check-in' })).toBeInTheDocument();
  });

  it('el diálogo de cancelar informa el plazo sin falta', async () => {
    stubApi({ ...signedIn(), 'GET /reservations/55': { body: reservation } });
    renderTurn();

    const dialog = await openCancelDialog();
    expect(dialog).toHaveAccessibleDescription(/sin falta hasta las 13:54 hs/);
    expect(dialog).toHaveAccessibleDescription(/después, cancelar cuenta como falta/);
  });

  it('cancela el turno y vuelve al inicio con un aviso', async () => {
    const fetchMock = stubApi({
      ...signedIn(),
      'GET /reservations/55': { body: reservation },
      'DELETE /reservations/55': { status: 204 },
    });
    renderTurn();

    const dialog = await openCancelDialog();
    expect(dialog).toHaveAccessibleDescription(/liberar tu lugar/);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar turno' }));

    expect(await screen.findByText('Inicio: Cancelaste tu turno.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/reservations/55',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('"Mantener turno" es la opción por defecto y cierra el diálogo sin cancelar', async () => {
    const fetchMock = stubApi({ ...signedIn(), 'GET /reservations/55': { body: reservation } });
    renderTurn();

    const dialog = await openCancelDialog();
    const [first, second] = within(dialog).getAllByRole('button');
    expect(first).toHaveAccessibleName('Mantener turno');
    expect(second).toHaveAccessibleName('Cancelar turno');
    await userEvent.click(first);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/reservations/55',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });

  it('si la cancelación cuenta como falta, lo dice en el título', async () => {
    stubApi({
      ...signedIn(),
      'GET /reservations/55': { body: { ...reservation, cancelCountsAsNoShow: true } },
    });
    renderTurn();

    const dialog = await openCancelDialog('Si cancelás ahora, cuenta como falta');

    expect(dialog).toHaveAccessibleDescription(/límite para cancelar/);
  });

  it('muestra el error si no se pudo cancelar', async () => {
    stubApi({
      ...signedIn(),
      'GET /reservations/55': { body: reservation },
      'DELETE /reservations/55': { status: 409, body: { message: 'La salida ya partió' } },
    });
    renderTurn();

    const dialog = await openCancelDialog();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar turno' }));

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
      'GET /reservations/55': { status: 500, body: { message: 'Error inesperado' } },
    });
    renderTurn();

    expect(await screen.findByRole('alert')).toHaveTextContent('Error inesperado');
  });
});
