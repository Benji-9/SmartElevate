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
};

export function Input({
  label,
  hint,
  error,
  id,
  className,
  'aria-describedby': describedByProp,
  ...rest
}: InputProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const hintId = hint ? `${inputId}-hint` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const describedBy = [hintId, errorId, describedByProp].filter(Boolean).join(' ') || undefined;

  return (
    <div className={['input', error && 'input--invalid', className].filter(Boolean).join(' ')}>
      <label htmlFor={inputId} className="input__label text-label">
        {label}
      </label>
      <input
        id={inputId}
        className="input__field text-body"
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...rest}
      />
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
