import { useId, type CSSProperties, type ReactNode } from 'react';
import './OptionGroup.css';

export type Option<T extends string> = {
  value: T;
  label: string;
  disabled?: boolean;
  /** Elemento decorativo antes del texto (p. ej. el punto de color del tipo de usuario). */
  adornment?: ReactNode;
};

type OptionGroupProps<T extends string> = {
  label: string;
  /** Nombre del grupo de radios; tiene que ser único en la pantalla. */
  name: string;
  options: Option<T>[];
  value: T | null;
  onChange: (value: T) => void;
  /** `wrap`: chips que ocupan lo que necesitan. `grid`: columnas iguales (`columns`). */
  layout?: 'wrap' | 'grid';
  columns?: number;
  hideLabel?: boolean;
  className?: string;
};

/**
 * Selector de una opción entre varias (edificio, piso, tipo de usuario, espera).
 * Usa radios nativos: flechas para moverse, Tab para salir del grupo.
 */
export function OptionGroup<T extends string>({
  label,
  name,
  options,
  value,
  onChange,
  layout = 'wrap',
  columns,
  hideLabel = false,
  className,
}: OptionGroupProps<T>) {
  const labelId = useId();
  const style = columns ? ({ '--option-columns': columns } as CSSProperties) : undefined;

  return (
    <div className={['option-group', className].filter(Boolean).join(' ')}>
      <span id={labelId} className={hideLabel ? 'visually-hidden' : 'option-group__label'}>
        {label}
      </span>
      <div
        role="radiogroup"
        aria-labelledby={labelId}
        className={`option-group__options option-group__options--${layout}`}
        style={style}
      >
        {options.map((option) => (
          <label
            key={option.value}
            className={['option', option.disabled && 'option--disabled'].filter(Boolean).join(' ')}
          >
            <input
              type="radio"
              className="visually-hidden"
              name={name}
              value={option.value}
              checked={value === option.value}
              disabled={option.disabled}
              onChange={() => onChange(option.value)}
            />
            {option.adornment}
            <span>{option.label}</span>
          </label>
        ))}
      </div>
    </div>
  );
}
