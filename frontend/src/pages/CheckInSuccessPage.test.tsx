import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { signedIn, stubApi } from '../test/stubApi';
import type { CheckInResult } from '../types/pending';
import { CheckInSuccessPage } from './CheckInSuccessPage';

const result: CheckInResult = {
  reservationId: 'r-1',
  outcome: 'ON_TIME',
  checkedInAt: '2026-09-29T17:04:30Z',
  waitDeltaSeconds: 20,
  elevatorName: 'Ascensor 2',
  coreName: 'L1',
  departsAt: '2026-09-29T17:04:00Z',
  durationMinutes: 2,
};

function renderPage(state: unknown = result) {
  render(
    <MemoryRouter initialEntries={[{ pathname: '/check-in/ok', state }]}>
      <Routes>
        <Route path="/" element={<p>Inicio</p>} />
        <Route path="/check-in/ok" element={<CheckInSuccessPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

const feedbackUrl = '/api/check-ins/r-1/wait-feedback';

describe('CheckInSuccessPage', () => {
  it('muestra el viaje registrado con ascensor, núcleo, hora y franja del turno', () => {
    stubApi(signedIn());
    renderPage();

    expect(screen.getByRole('heading', { level: 1, name: 'Viaje registrado' })).toBeVisible();
    expect(screen.getByText('Ascensor 2 · L1 · 14:04')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Check-in a tiempo' })).toBeInTheDocument();
    expect(screen.getByText('14:04 – 14:06')).toBeInTheDocument();
    expect(screen.getByText('Nos ayuda a medir la congestión real.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute('href', '/');
  });

  it.each([
    ['OTHER_ELEVATOR', 'Check-in en otro ascensor', /ascensor distinto/],
    ['LATE', 'Check-in fuera de hora', /cuenta como falta/],
  ] as const)('explica el resultado %s con texto', (outcome, title, detail) => {
    stubApi(signedIn());
    renderPage({ ...result, outcome });

    expect(screen.getByRole('heading', { name: title })).toBeInTheDocument();
    expect(screen.getByText(detail)).toBeInTheDocument();
  });

  it('envía la encuesta una sola vez', async () => {
    const fetchMock = stubApi({
      ...signedIn(),
      'POST /check-ins/r-1/wait-feedback': { status: 204 },
    });
    renderPage();

    const group = screen.getByRole('radiogroup', { name: '¿Cuánto esperaste el ascensor?' });
    expect(group).toBeInTheDocument();
    const send = screen.getByRole('button', { name: 'Enviar respuesta' });
    expect(send).toBeDisabled();

    await userEvent.click(screen.getByRole('radio', { name: '5–10 min' }));
    await userEvent.click(send);

    expect(await screen.findByRole('status')).toHaveTextContent('Registramos tu respuesta');
    expect(fetchMock).toHaveBeenCalledWith(
      feedbackUrl,
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ range: 'FROM_5_TO_10' }) }),
    );
    expect(screen.queryByRole('button', { name: 'Enviar respuesta' })).not.toBeInTheDocument();
    for (const radio of screen.getAllByRole('radio')) expect(radio).toBeDisabled();
    expect(fetchMock.mock.calls.filter(([url]) => url === feedbackUrl)).toHaveLength(1);
  });

  it('si ya se había respondido (409), lo avisa y no deja reenviar', async () => {
    stubApi({
      ...signedIn(),
      'POST /check-ins/r-1/wait-feedback': { status: 409, body: { message: 'Ya respondida' } },
    });
    renderPage();

    await userEvent.click(screen.getByRole('radio', { name: '< 2 min' }));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar respuesta' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Ya respondiste esta encuesta.');
    expect(screen.queryByRole('button', { name: 'Enviar respuesta' })).not.toBeInTheDocument();
  });

  it('muestra el error y deja reintentar', async () => {
    stubApi({
      ...signedIn(),
      'POST /check-ins/r-1/wait-feedback': { status: 500, body: { message: 'Error inesperado' } },
    });
    renderPage();

    await userEvent.click(screen.getByRole('radio', { name: '> 10 min' }));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar respuesta' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Error inesperado');
    expect(screen.getByRole('button', { name: 'Enviar respuesta' })).toBeEnabled();
  });

  it('sin un check-in reciente redirige al inicio', () => {
    stubApi(signedIn());
    renderPage(null);

    expect(screen.getByText('Inicio')).toBeInTheDocument();
  });
});
