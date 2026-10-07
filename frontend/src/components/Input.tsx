import { useId, type InputHTMLAttributes } from 'react';
import './Input.css';

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  /** Texto de ayuda debajo del campo. */
  hint?: string;
  /**
   * Mensaje de error: marca el campo como inválido, lo asocia con `aria-describedby` y se
   * anuncia al aparecer (`role="alert"`).
   */
  error?: string;
  /** Texto fijo a la derecha, dentro del control (p. ej. `@uade.edu.ar`). Se anuncia como descripción. */
  suffix?: string;
};

export function Input({
  label,
  hint,
  error,
  suffix,
  id,
  className,
  'aria-describedby': describedByProp,
  ...rest
}: InputProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const suffixId = suffix ? `${inputId}-suffix` : undefined;
  const hintId = hint ? `${inputId}-hint` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const describedBy =
    [suffixId, hintId, errorId, describedByProp].filter(Boolean).join(' ') || undefined;

  const field = (
    <input
      id={inputId}
      className="input__field text-body"
      aria-invalid={error ? true : undefined}
      aria-describedby={describedBy}
      {...rest}
    />
  );

  return (
    <div className={['input', error && 'input--invalid', className].filter(Boolean).join(' ')}>
      <label htmlFor={inputId} className="input__label text-label">
        {label}
      </label>
      {suffix ? (
        <div className="input__control">
          {field}
          <span id={suffixId} className="input__suffix text-body">
            {suffix}
          </span>
        </div>
      ) : (
        field
      )}
      {hint && (
        <p id={hintId} className="input__hint text-label">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="input__error text-label">
          {error}
        </p>
      )}
    </div>
  );
}
