import { useId, type Ref } from 'react';
import type { Departure } from '../../types/pending';
import { formatSlot } from './format';

type SlotListProps = {
  departures: Departure[];
  value: string | null;
  onChange: (departureId: string) => void;
  /** Para llevar el foco al título (p. ej. cuando la salida elegida se llenó). */
  labelRef?: Ref<HTMLSpanElement>;
};

/** Salidas de ascensor para reservar, con su ocupación. Las completas no se pueden elegir. */
export function SlotList({ departures, value, onChange, labelRef }: SlotListProps) {
  const labelId = useId();
  return (
    <div className="option-group">
      <span id={labelId} ref={labelRef} tabIndex={-1} className="option-group__label">
        Franja horaria
      </span>
      <div role="radiogroup" aria-labelledby={labelId} className="slot-list">
        {departures.map((d) => {
          const full = d.occupied >= d.capacity;
          return (
            <label key={d.id} className={['slot', full && 'slot--full'].filter(Boolean).join(' ')}>
              <span className="slot__info">
                <span className="slot__time">{formatSlot(d.departsAt, d.durationMinutes)}</span>
                <span className="slot__bar" aria-hidden="true">
                  <span style={{ width: `${Math.min(100, (d.occupied / d.capacity) * 100)}%` }} />
                </span>
              </span>
              {/* Separa la hora del cupo en el nombre accesible ("14:32 – 14:34 Completo"). */}{' '}
              {full ? (
                <span className="slot__count">Completo</span>
              ) : (
                <span className="slot__count">
                  <span aria-hidden="true">
                    {d.occupied}/{d.capacity}
                  </span>
                  <span className="visually-hidden">
                    {d.occupied} de {d.capacity} lugares ocupados
                  </span>
                </span>
              )}
              <input
                type="radio"
                className="slot__radio"
                name="departure"
                value={d.id}
                checked={value === d.id}
                disabled={full}
                onChange={() => onChange(d.id)}
              />
            </label>
          );
        })}
      </div>
    </div>
  );
}
