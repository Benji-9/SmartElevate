import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { signedOut, stubApi } from '../test/stubApi';

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('Páginas legales', () => {
  it('el footer del login lleva a privacidad y a términos, sin sesión', async () => {
    stubApi(signedOut);
    renderAt('/login');

    const footer = await screen.findByRole('navigation', { name: 'Información legal' });
    await userEvent.click(within(footer).getByRole('link', { name: 'Privacidad' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Política de privacidad' }),
    ).toBeInTheDocument();

    const next = screen.getByRole('navigation', { name: 'Información legal' });
    await userEvent.click(within(next).getByRole('link', { name: 'Términos de uso' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Términos de uso' }),
    ).toBeInTheDocument();
  });

  it('la política trae la leyenda de la AAIP y la sección de datos de salud', async () => {
    stubApi(signedOut);
    renderAt('/privacidad');

    expect(await screen.findByRole('heading', { name: '3. Datos de salud' })).toBeInTheDocument();
    expect(screen.getByText(/Órgano de Control de la Ley N° 25.326/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sitio de la AAIP' })).toHaveAttribute(
      'href',
      'https://www.argentina.gob.ar/aaip',
    );
  });

  it('el registro avisa que crear la cuenta acepta términos y privacidad', async () => {
    stubApi(signedOut);
    renderAt('/registro');

    const notice = (await screen.findByText(/Al crear tu cuenta aceptás/)).closest('p')!;
    expect(within(notice).getByRole('link', { name: 'Términos de uso' })).toHaveAttribute(
      'href',
      '/terminos',
    );
    expect(within(notice).getByRole('link', { name: 'Política de privacidad' })).toHaveAttribute(
      'href',
      '/privacidad',
    );
  });
});
