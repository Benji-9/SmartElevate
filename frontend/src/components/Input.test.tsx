import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Input } from './Input';

describe('Input', () => {
  it('asocia el label con el campo', async () => {
    render(<Input label="Email institucional" placeholder="nombre@uade.edu.ar" />);

    const field = screen.getByLabelText('Email institucional');
    await userEvent.type(field, 'jmartinez@uade.edu.ar');

    expect(field).toHaveValue('jmartinez@uade.edu.ar');
    expect(field).not.toHaveAttribute('aria-invalid');
  });

  it('marca el error y lo usa como descripción accesible', () => {
    render(
      <Input
        label="Email institucional"
        hint="Usá tu cuenta de UADE."
        error="Tiene que ser un email @uade.edu.ar."
      />,
    );

    const field = screen.getByLabelText('Email institucional');
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(field).toHaveAccessibleDescription(
      'Usá tu cuenta de UADE. Tiene que ser un email @uade.edu.ar.',
    );
  });
});
