import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { ScreenHeader } from './ScreenHeader';

describe('ScreenHeader', () => {
  it('tiene un botón "Volver" que se usa con teclado', async () => {
    render(
      <MemoryRouter initialEntries={['/turno']}>
        <Routes>
          <Route path="/turno" element={<ScreenHeader title="Tu turno" />} />
          <Route path="/" element={<p>Inicio</p>} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { level: 1, name: 'Tu turno' })).toBeInTheDocument();

    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'Volver' })).toHaveFocus();
    await userEvent.keyboard('{Enter}');

    expect(screen.getByText('Inicio')).toBeInTheDocument();
  });
});
