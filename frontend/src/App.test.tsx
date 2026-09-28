import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App (smoke)', () => {
  it('renderiza la home, la navegación y el estado de la API', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ status: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    renderAt('/');

    expect(screen.getByRole('heading', { level: 1, name: 'SmartElevate' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Solicitar turno' })).toBeInTheDocument();
    expect(await screen.findByText('API conectada')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/ping', expect.anything());
  });

  it('muestra la API como no disponible si el ping falla', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')));

    renderAt('/');

    expect(await screen.findByText('API no disponible')).toBeInTheDocument();
  });

  it('renderiza las páginas placeholder', () => {
    renderAt('/ascensores');
    expect(screen.getByRole('heading', { name: 'Estado de ascensores' })).toBeInTheDocument();
  });
});
