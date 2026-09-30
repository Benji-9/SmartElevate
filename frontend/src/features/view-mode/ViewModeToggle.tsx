import type { KeyboardEvent } from 'react';
import { useViewMode, type ViewMode } from './useViewMode';

const options: [ViewMode, string][] = [
  ['pantalla', 'Pantalla'],
  ['telefono', 'Teléfono'],
];

const arrows = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'];

/** Selector de vista (VIEW-MODES.md §3): radiogroup con foco itinerante y flechas. */
export function ViewModeToggle() {
  const { mode, setMode } = useViewMode();

  // Con dos opciones, cualquier flecha va a la otra (en los dos sentidos da la vuelta).
  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (!arrows.includes(event.key)) return;
    event.preventDefault();
    const other = event.currentTarget.querySelector<HTMLButtonElement>('[aria-checked="false"]');
    setMode(mode === 'pantalla' ? 'telefono' : 'pantalla');
    other?.focus();
  }

  return (
    <div
      role="radiogroup"
      aria-label="Vista de la aplicación"
      className="view-toggle"
      onKeyDown={onKeyDown}
    >
      {options.map(([value, label]) => (
        <button
          key={value}
          type="button"
          role="radio"
          aria-checked={mode === value}
          tabIndex={mode === value ? 0 : -1}
          className="view-toggle__option text-body-sm-strong"
          onClick={() => setMode(value)}
        >
          {label}
        </button>
      ))}
    </div>
  );
}
