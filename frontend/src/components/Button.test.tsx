import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button';

describe('Button', () => {
  it('ejecuta onClick al hacer click', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Ingresar</Button>);

    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(onClick).toHaveBeenCalledOnce();
  });

  it('no se puede usar mientras carga y lo anuncia', async () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        Ingresar
      </Button>,
    );

    const button = screen.getByRole('button', { name: /Ingresar/ });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(button).toHaveTextContent('Cargando…');

    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('es type="button" por defecto para no enviar formularios sin querer', () => {
    render(<Button variant="secondary">Cancelar turno</Button>);
    expect(screen.getByRole('button', { name: 'Cancelar turno' })).toHaveAttribute(
      'type',
      'button',
    );
  });
});
