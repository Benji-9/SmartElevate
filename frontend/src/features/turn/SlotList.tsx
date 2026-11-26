import { useId, type Ref } from 'react';
import type { Slot } from './catalog';
import { formatTime } from './format';

type SlotListProps = {
  slots: Slot[];
  /** `departureAt` de la salida elegida. */
  value: string | null;
  onChange: (departureAt: string) => void;
  /** Para llevar el foco al título (p. ej. cuando la salida elegida se llenó). */
  labelRef?: Ref<HTMLSpanElement>;
};

/**
 * Salidas para reservar, con los lugares que el usuario puede tomar (los manda el servidor). El
 * ascensor no se muestra: lo asigna la app y se ve después de reservar (ADR 0025). Las que no
 * tienen lugar siguen enfocables (aria-disabled) pero no se pueden elegir.
 */
export function SlotList({ slots, value, onChange, labelRef }: SlotListProps) {
  const labelId = useId();
  return (
    <div className="option-group">
      <span id={labelId} ref={labelRef} tabIndex={-1} className="option-group__label">
        Horario de salida
      </span>
      <div role="radiogroup" aria-labelledby={labelId} className="slot-list">
        {slots.map(({ departureAt, departure: d }) => {
          const full = d.remainingSeats <= 0;
          return (
            <label
              key={departureAt}
              className={['slot', full && 'slot--full'].filter(Boolean).join(' ')}
            >
              <span className="slot__info">
                <span className="slot__time">{formatTime(departureAt)}</span>
                <span className="slot__bar" aria-hidden="true">
                  <span
                    style={{ width: `${Math.min(100, (d.bookedSeats / d.capacity) * 100)}%` }}
                  />
                </span>
              </span>{' '}
              <span className="slot__count">
                {full
                  ? 'Completo'
                  : `${d.remainingSeats} ${d.remainingSeats === 1 ? 'libre' : 'libres'}`}
              </span>
              <input
                type="radio"
                className="slot__radio"
                name="departure"
                value={departureAt}
                checked={value === departureAt}
                aria-disabled={full || undefined}
                onChange={() => {
                  // Enfocable pero no elegible: el lector anuncia "no disponible" y no cambia nada.
                  if (!full) onChange(departureAt);
                }}
              />
            </label>
          );
        })}
      </div>
    </div>
  );
}
