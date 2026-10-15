import { useId, type Ref } from 'react';
import type { Slot } from './catalog';
import { formatTime } from './format';

type SlotListProps = {
  slots: Slot[];
  /** `departureAt` de la franja elegida. */
  value: string | null;
  onChange: (departureAt: string) => void;
  /** Para llevar el foco al título (p. ej. cuando la salida elegida se llenó). */
  labelRef?: Ref<HTMLSpanElement>;
};

/**
 * Salidas para reservar, con su ascensor y los lugares que el usuario puede tomar (los manda el
 * servidor). Las que no tienen lugar no se pueden elegir.
 */
export function SlotList({ slots, value, onChange, labelRef }: SlotListProps) {
  const labelId = useId();
  return (
    <div className="option-group">
      <span id={labelId} ref={labelRef} tabIndex={-1} className="option-group__label">
        Franja horaria
      </span>
      <div role="radiogroup" aria-labelledby={labelId} className="slot-list">
        {slots.map(({ departureAt, departure: d }) => {
          const full = d.remainingSeats <= 0;
          const taken = d.capacity - d.remainingSeats;
          return (
            <label
              key={departureAt}
              className={['slot', full && 'slot--full'].filter(Boolean).join(' ')}
            >
              <span className="slot__info">
                <span className="slot__time">
                  {formatTime(departureAt)}
                  {/* Separa la hora del ascensor en el nombre accesible. */}{' '}
                  <span className="slot__elevator">· Ascensor {d.elevator}</span>
                </span>
                <span className="slot__bar" aria-hidden="true">
                  <span style={{ width: `${Math.min(100, (taken / d.capacity) * 100)}%` }} />
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
                disabled={full}
                onChange={() => onChange(departureAt)}
              />
            </label>
          );
        })}
      </div>
    </div>
  );
}
