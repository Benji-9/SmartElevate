import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmAction } from './ConfirmAction';

function setup() {
  const onConfirm = vi.fn();
  render(
    <ConfirmAction
      label="Rechazar"
      name="Rechazar a Ana"
      question="¿Rechazar a Ana?"
      confirmLabel="Sí, rechazar"
      onConfirm={onConfirm}
    />,
  );
  return onConfirm;
}

describe('ConfirmAction', () => {
  it('pregunta con el foco en Cancelar y el grupo se nombra por la pregunta', async () => {
    setup();
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar a Ana' }));

    expect(screen.getByRole('group', { name: '¿Rechazar a Ana?' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus();
  });

  it('al cancelar devuelve el foco al botón inicial sin confirmar', async () => {
    const onConfirm = setup();
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar a Ana' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.getByRole('button', { name: 'Rechazar a Ana' })).toHaveFocus();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('al confirmar ejecuta y deja el foco en el botón inicial', async () => {
    const onConfirm = setup();
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar a Ana' }));
    await userEvent.click(screen.getByRole('button', { name: 'Sí, rechazar' }));

    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Rechazar a Ana' })).toHaveFocus();
  });
});
