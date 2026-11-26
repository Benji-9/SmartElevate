import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { stubApi } from '../../test/stubApi';
import type { CongestionSnapshot } from '../../types/api';
import { CongestionNow } from './CongestionNow';

const snapshot: CongestionSnapshot = {
  updatedAt: new Date().toISOString(),
  refreshAfterSeconds: 30,
  cores: [{ coreId: 'L1', name: 'Lima 1', level: 'HIGH', occupancyPercent: 90 }],
};

afterEach(() => {
  vi.useRealTimers();
});

describe('CongestionNow', () => {
  it('sigue consultando tras un error y se recupera solo cuando vuelve el servidor', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let calls = 0;
    const fetchMock = stubApi({
      'GET /congestion': () =>
        ++calls === 1 ? { status: 503, body: { message: 'Servidor caído' } } : { body: snapshot },
    });
    render(<CongestionNow />);

    expect(await screen.findByRole('alert')).toBeInTheDocument();

    await act(() => vi.advanceTimersByTimeAsync(15_000));

    expect(await screen.findByText('Lima 1')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  /** Falla la primera vez y después responde. */
  const failsOnce = () => {
    let calls = 0;
    return stubApi({
      'GET /congestion': () =>
        ++calls === 1 ? { status: 503, body: { message: 'Servidor caído' } } : { body: snapshot },
    });
  };
  const heading = () => screen.getByRole('heading', { name: 'Congestión ahora' });

  it('"Reintentar" no pierde el foco: queda en el título de la sección', async () => {
    failsOnce();
    render(<CongestionNow />);

    await userEvent.click(await screen.findByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Lima 1')).toBeInTheDocument();
    expect(heading()).toHaveFocus();
  });

  it('si se recupera solo con el foco en "Reintentar", tampoco lo pierde', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    failsOnce();
    render(<CongestionNow />);
    (await screen.findByRole('button', { name: 'Reintentar' })).focus();

    await act(() => vi.advanceTimersByTimeAsync(15_000));

    expect(await screen.findByText('Lima 1')).toBeInTheDocument();
    await waitFor(() => expect(heading()).toHaveFocus());
  });
});
