import { useId } from 'react';
import './Switch.css';

type SwitchProps = {
  label: string;
  description?: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
};

/** Interruptor on/off: checkbox nativo con `role="switch"` (Espacio lo cambia). */
export function Switch({ label, description, checked, onChange }: SwitchProps) {
  const labelId = useId();
  const descriptionId = useId();

  // El <label> que envuelve hace clickeable toda la fila; el nombre accesible es solo `label`.
  return (
    <label className="switch">
      <span className="switch__text">
        <span id={labelId} className="switch__label">
          {label}
        </span>
        {description && (
          <span id={descriptionId} className="switch__description">
            {description}
          </span>
        )}
      </span>
      <input
        type="checkbox"
        role="switch"
        className="switch__input"
        checked={checked}
        aria-labelledby={labelId}
        aria-describedby={description ? descriptionId : undefined}
        onChange={(event) => onChange(event.target.checked)}
      />
    </label>
  );
}
