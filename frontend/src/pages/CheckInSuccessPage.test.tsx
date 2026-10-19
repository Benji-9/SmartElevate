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
      screen.getByText('Subiste dentro de tu franja. ¡Gracias por ayudar a que el ascensor fluya!'),
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
    ['ON_TIME', 'Viaje registrado', 'Check-in a tiempo', /dentro de tu franja/],
    [
      'OTHER_ELEVATOR',
      'Viaje registrado en otro ascensor',
      'Check-in en otro ascensor',
      'Reservaste el ascensor 33 y escaneaste el 34. Quedó registrado y no cuenta como falta.',
    ],
    [
      'LATE',
      'Llegaste fuera de tu franja: cuenta como falta',
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

  it('sin un check-in reciente redirige al inicio', () => {
    stubApi(signedIn());
    renderPage(null);

    expect(screen.getByText('Inicio')).toBeInTheDocument();
  });
});
