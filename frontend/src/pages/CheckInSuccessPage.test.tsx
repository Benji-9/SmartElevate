import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { signedIn, stubApi } from '../test/stubApi';
import type { CheckIn } from '../types/api';
import { CheckInSuccessPage } from './CheckInSuccessPage';

const result: CheckIn = {
  reservationId: 55,
  outcome: 'ON_TIME',
  reservationStatus: 'USED',
  elevator: '33',
  reservedElevator: '33',
  departureAt: '2026-09-29T17:04:00Z',
  checkedInAt: '2026-09-29T17:04:30Z',
  source: 'REAL',
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

describe('CheckInSuccessPage', () => {
  it('muestra el viaje registrado con ascensor, hora y salida del turno', () => {
    stubApi(signedIn());
    renderPage();

    expect(screen.getByRole('heading', { level: 1, name: 'Viaje registrado' })).toBeVisible();
    expect(screen.getByText('Ascensor 33 · 14:04 hs')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { level: 2, name: 'Check-in a tiempo' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Subiste a tiempo a tu salida. ¡Gracias por ayudar a que el ascensor fluya!',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('14:04 hs')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute('href', '/');
  });

  it('sin backend para la encuesta de espera, no la muestra', () => {
    stubApi(signedIn());
    renderPage();

    expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument();
  });

  it.each([
    ['ON_TIME', 'Viaje registrado', 'Check-in a tiempo', /Subiste a tiempo a tu salida/],
    [
      'OTHER_ELEVATOR',
      'Viaje registrado en otro ascensor',
      'Check-in en otro ascensor',
      'Reservaste el ascensor 33 y escaneaste el 34. Quedó registrado y no cuenta como falta.',
    ],
    [
      'LATE',
      'Llegaste tarde: se registró como falta',
      'Check-in fuera de hora',
      /después de la hora de tu salida, así que cuenta como falta/,
    ],
  ] as const)('explica el resultado %s en el título y la tarjeta', (outcome, h1, title, detail) => {
    stubApi(signedIn());
    renderPage({ ...result, outcome, elevator: outcome === 'OTHER_ELEVATOR' ? '34' : '33' });

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(h1);
    expect(screen.getByRole('heading', { level: 2, name: title })).toBeInTheDocument();
    expect(screen.getByText(detail)).toBeInTheDocument();
  });

  it('con LATE no muestra el título de éxito', () => {
    stubApi(signedIn());
    renderPage({ ...result, outcome: 'LATE', reservationStatus: 'NO_SHOW' });

    expect(screen.queryByRole('heading', { name: /registrado/ })).not.toBeInTheDocument();
  });

  const late: CheckIn = { ...result, outcome: 'LATE', reservationStatus: 'NO_SHOW' };
  const noShows = {
    recentNoShows: 2,
    threshold: 4,
    windowDays: 10,
    suspensionHours: 48,
    suspendedUntil: null,
    exempt: false,
  };

  it('con LATE dice cuántas faltas lleva y qué pasa al llegar al umbral del servidor', async () => {
    stubApi({ ...signedIn(), 'GET /me/no-shows': { body: noShows } });
    renderPage(late);

    const notice = await screen.findByRole('region', { name: 'Tenés faltas' });
    expect(notice).toHaveTextContent('Tenés 2 faltas en los últimos 10 días.');
    expect(notice).toHaveTextContent('Con 4 faltas en 10 días no vas a poder reservar por 48 h.');
  });

  it('con LATE y suspensión dice el límite y hasta cuándo no puede reservar', async () => {
    stubApi({
      ...signedIn(),
      'GET /me/no-shows': {
        body: { ...noShows, recentNoShows: 4, suspendedUntil: '2026-09-30T20:00:00Z' },
      },
    });
    renderPage(late);

    const notice = await screen.findByRole('region', { name: 'No podés reservar por ahora' });
    expect(notice).toHaveTextContent('El límite es de 4 faltas en 10 días.');
    expect(notice).toHaveTextContent(
      'Vas a poder volver a reservar el miércoles, 30 de septiembre a las 17:00.',
    );
  });

  it('con LATE avisa que está cargando las faltas y, si falla, muestra el motivo', async () => {
    stubApi({
      ...signedIn(),
      'GET /me/no-shows': { status: 500, body: { message: 'Error interno' } },
    });
    renderPage(late);

    expect(screen.getByRole('status')).toHaveTextContent('Cargando tus faltas…');
    expect(await screen.findByRole('alert')).toHaveTextContent('Error interno');
  });

  it('un check-in simulado fuera de hora no se presenta como falta', () => {
    const fetchMock = stubApi(signedIn());
    renderPage({ ...late, source: 'SIMULATED' });

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'Llegaste tarde: check-in simulado, sin falta',
    );
    expect(
      screen.getByText(
        'El check-in fue después de la hora de tu salida. Como es simulado, no cuenta como falta.',
      ),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith('/api/me/no-shows', expect.anything());
  });

  it('sin un check-in reciente redirige al inicio', () => {
    stubApi(signedIn());
    renderPage(null);

    expect(screen.getByText('Inicio')).toBeInTheDocument();
  });
});
