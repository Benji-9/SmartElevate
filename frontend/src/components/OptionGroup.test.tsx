import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { OptionGroup, type Option } from './OptionGroup';

type UserType = 'STUDENT' | 'TEACHER' | 'STAFF';

const options: Option<UserType>[] = [
  { value: 'STUDENT', label: 'Estudiante' },
  { value: 'TEACHER', label: 'Docente' },
  { value: 'STAFF', label: 'Personal', disabled: true },
];

function ControlledGroup({ onChange }: { onChange: (value: UserType) => void }) {
  const [value, setValue] = useState<UserType | null>('STUDENT');
  return (
    <OptionGroup
      label="Tipo de usuario"
      name="user-type"
      options={options}
      value={value}
      onChange={(next) => {
        setValue(next);
        onChange(next);
      }}
    />
  );
}

describe('OptionGroup', () => {
  it('expone un radiogroup con nombre y la opción elegida', () => {
    render(<ControlledGroup onChange={vi.fn()} />);

    expect(screen.getByRole('radiogroup', { name: 'Tipo de usuario' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Estudiante' })).toBeChecked();
  });

  it('elige una opción con click', async () => {
    const onChange = vi.fn();
    render(<ControlledGroup onChange={onChange} />);

    await userEvent.click(screen.getByRole('radio', { name: 'Docente' }));

    expect(onChange).toHaveBeenCalledWith('TEACHER');
    expect(screen.getByRole('radio', { name: 'Docente' })).toBeChecked();
  });

  it('se puede usar con teclado', async () => {
    const onChange = vi.fn();
    render(<ControlledGroup onChange={onChange} />);

    await userEvent.tab();
    expect(screen.getByRole('radio', { name: 'Estudiante' })).toHaveFocus();
    await userEvent.keyboard('{ArrowRight}');

    expect(onChange).toHaveBeenCalledWith('TEACHER');
  });

  it('no permite elegir una opción deshabilitada', async () => {
    const onChange = vi.fn();
    render(<ControlledGroup onChange={onChange} />);

    const disabled = screen.getByRole('radio', { name: 'Personal' });
    expect(disabled).toBeDisabled();
    await userEvent.click(disabled);

    expect(onChange).not.toHaveBeenCalled();
  });
});
