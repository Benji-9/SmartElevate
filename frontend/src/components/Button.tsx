import type { ButtonHTMLAttributes } from 'react';
import './Button.css';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  /** `primary` (Figma Button/Primary) o `secondary` (Figma Button/Secondary). */
  variant?: 'primary' | 'secondary';
  /** Deshabilita el botón y anuncia `loadingLabel` a los lectores de pantalla. */
  loading?: boolean;
  loadingLabel?: string;
  /**
   * `false`: en el layout de Pantalla (contenedor `app` ≥ 900 px) toma el ancho del contenido
   * (VIEW-MODES.md §6). En el móvil los botones siempre van a ancho completo (SCREENS.md).
   */
  block?: boolean;
};

export function Button({
  variant = 'primary',
  loading = false,
  loadingLabel = 'Cargando…',
  block = true,
  disabled,
  type = 'button',
  className,
  children,
  ...rest
}: ButtonProps) {
  const classes = ['button', `button--${variant}`, !block && 'button--inline', className]
    .filter(Boolean)
    .join(' ');
  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <span className="button__spinner" aria-hidden="true" />}
      {children}
      {loading && <span className="visually-hidden">{loadingLabel}</span>}
    </button>
  );
}
