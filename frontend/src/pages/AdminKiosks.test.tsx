import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { simulatedKiosks } from '../features/kiosk/simulatedKiosks';
import { signedIn, stubApi, testUser } from '../test/stubApi';
import type { Elevator, Kiosk } from '../types/api';

const elevator = (code: string, coreName: string): Elevator => ({
  code,
  coreName,
  building: 'INDEPENDENCIA',
  core: 'IND2',
  floors: [0, 7, 8],
  usage: 'COMMON',
  active: true,
});
const ELEVATORS = [elevator('33', 'Independencia 2'), elevator('34', 'Independencia 2')];

/** Servidor simulado con estado: el alta y la revocación cambian la lista. */
function renderAdmin(path: string, { simulatorEnabled = true } = {}) {
  let kiosks: Kiosk[] = [];
  let created = 0;
  /** Ascensor de cada credencial: el token lo devuelve como `elevatorId`. */
  const elevatorOf = new Map<string, string>();
  const fetchMock = stubApi({
    ...signedIn({ ...testUser, role: 'ADMIN' }),
    'GET /elevators': { body: ELEVATORS },
    'GET /admin/kiosks': () => ({ body: { simulatorEnabled, kiosks } }),
    'POST /admin/kiosks': (init) => {
      const { elevatorId, simulated = false } = JSON.parse(String(init?.body));
      created += 1;
      const kiosk = {
        id: `k-${created}`,
        elevatorId,
        simulated,
        createdAt: '2026-10-14T18:40:00Z',
      };
      kiosks = [...kiosks, { ...kiosk, lastSeenAt: null, revokedAt: null }];
      elevatorOf.set(`secreto-${created}`, elevatorId);
      return { status: 201, body: { ...kiosk, secret: `secreto-${created}` } };
    },
    'POST /admin/kiosks/k-1/revoke': () => {
      kiosks = kiosks.map((k) => ({ ...k, revokedAt: '2026-10-14T18:45:00Z' }));
      return { status: 204 };
    },
    'GET /kiosks/me/qr-token': (init) => ({
      body: {
        token: 'token-1',
        expiresAt: new Date(Date.now() + 30_000).toISOString(),
        elevatorId: elevatorOf.get(
          new Headers(init?.headers).get('Authorization')!.replace('Bearer ', ''),
        ),
      },
    }),
  });
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
  const calls = (method: string, suffix: string) =>
    fetchMock.mock.calls.filter(
      ([url, init]) => (init?.method ?? 'GET') === method && String(url).endsWith(suffix),
    );
  return { calls };
}

beforeEach(() => simulatedKiosks.clear());

describe('AdminKiosksPage', () => {
  it('muestra la credencial del alta una sola vez', async () => {
    const { calls } = renderAdmin('/admin/kioscos');
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

    expect(await screen.findByText('Todavía no hay kioscos.')).toBeInTheDocument();
    await user.selectOptions(await screen.findByLabelText('Ascensor'), '33');
    await user.click(screen.getByRole('button', { name: 'Dar de alta' }));

    expect(await screen.findByText('secreto-1')).toBeInTheDocument();
    expect(screen.getByText(/no la vamos a volver a mostrar/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Copiar credencial' }));
    expect(writeText).toHaveBeenCalledWith('secreto-1');
    expect(await screen.findByText('Copiada')).toBeInTheDocument();

    const table = await screen.findByRole('table', { name: 'Kioscos' });
    expect(within(table).getByRole('rowheader', { name: '33' })).toBeInTheDocument();
    expect(within(table).getByText('Activo')).toBeInTheDocument();
    expect(within(table).getByText('Nunca')).toBeInTheDocument();
    expect(calls('POST', '/admin/kiosks')[0][1]?.body).toBe(JSON.stringify({ elevatorId: '33' }));

    await user.click(screen.getByRole('button', { name: 'Listo, ya la guardé' }));
    expect(screen.queryByText('secreto-1')).not.toBeInTheDocument();
  });

  it('revocar pide confirmación y marca el kiosco como revocado', async () => {
    const { calls } = renderAdmin('/admin/kioscos');
    const user = userEvent.setup();
    await user.selectOptions(await screen.findByLabelText('Ascensor'), '33');
    await user.click(screen.getByRole('button', { name: 'Dar de alta' }));

    await user.click(
      await screen.findByRole('button', { name: 'Revocar el kiosco del ascensor 33' }),
    );
    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus();
    await user.click(screen.getByRole('button', { name: 'Sí, revocar' }));

    expect(await screen.findByText('Revocado')).toBeInTheDocument();
    expect(calls('POST', '/admin/kiosks/k-1/revoke')).toHaveLength(1);
    expect(
      screen.queryByRole('button', { name: 'Revocar el kiosco del ascensor 33' }),
    ).not.toBeInTheDocument();
  });
});

describe('AdminKioskSimulatorPage', () => {
  it('con el simulador apagado no aparece en el menú ni se puede usar', async () => {
    const { calls } = renderAdmin('/admin/simulador', { simulatorEnabled: false });

    expect(
      await screen.findByText('El simulador de kiosco está apagado en este entorno.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Kioscos' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Simulador de kiosco' })).not.toBeInTheDocument();
    expect(screen.queryByText('MODO SIMULADOR')).not.toBeInTheDocument();
    expect(calls('POST', '/admin/kiosks')).toHaveLength(0);
  });

  it('con el simulador habilitado muestra el cartel y el QR de un kiosco simulado', async () => {
    const { calls } = renderAdmin('/admin/simulador');
    const user = userEvent.setup();

    expect(await screen.findByText('MODO SIMULADOR')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Simulador de kiosco' })).toHaveAttribute(
      'aria-current',
      'page',
    );

    await user.selectOptions(await screen.findByLabelText('Ascensor'), '33');
    expect(
      await screen.findByRole('img', { name: 'Código QR del ascensor 33 para hacer check-in' }),
    ).toBeInTheDocument();
    expect(calls('POST', '/admin/kiosks')[0][1]?.body).toBe(
      JSON.stringify({ elevatorId: '33', simulated: true }),
    );
    // El token se pide con la credencial del kiosco simulado, no con el JWT del admin.
    const [, init] = calls('GET', '/kiosks/me/qr-token')[0];
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer secreto-1');
    expect(screen.queryByText('secreto-1')).not.toBeInTheDocument();

    // Volver a un ascensor ya usado reutiliza su kiosco.
    await user.selectOptions(screen.getByLabelText('Ascensor'), '34');
    await screen.findByRole('img', { name: 'Código QR del ascensor 34 para hacer check-in' });
    await user.selectOptions(screen.getByLabelText('Ascensor'), '33');
    await screen.findByRole('img', { name: 'Código QR del ascensor 33 para hacer check-in' });
    expect(calls('POST', '/admin/kiosks')).toHaveLength(2);
  });
});
