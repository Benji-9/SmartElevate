import { act, render, screen } from '@testing-library/react';
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
});
