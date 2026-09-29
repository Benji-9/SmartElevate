import { useId } from 'react';
import { Chip } from '../../components/Chip';
import type { Departure } from '../../types/pending';
import { formatSlot } from './format';

type SlotListProps = {
  departures: Departure[];
  value: string | null;
  onChange: (departureId: string) => void;
};

/** Salidas de ascensor para reservar, con su ocupación. Las completas no se pueden elegir. */
export function SlotList({ departures, value, onChange }: SlotListProps) {
  const labelId = useId();
  return (
    <div className="option-group">
      <span id={labelId} className="option-group__label">
        Salida
      </span>
      <div role="radiogroup" aria-labelledby={labelId} className="slot-list">
        {departures.map((d) => {
          const full = d.occupied >= d.capacity;
          return (
            <label key={d.id} className={['slot', full && 'slot--full'].filter(Boolean).join(' ')}>
              <input
                type="radio"
                className="visually-hidden"
                name="departure"
                value={d.id}
                checked={value === d.id}
                disabled={full}
                onChange={() => onChange(d.id)}
              />
              <span className="slot__time">{formatSlot(d.departsAt, d.durationMinutes)}</span>
              <span className="slot__bar" aria-hidden="true">
                <span style={{ width: `${Math.min(100, (d.occupied / d.capacity) * 100)}%` }} />
              </span>
              {full ? (
                <Chip tone="danger">Completo</Chip>
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
            </label>
          );
        })}
      </div>
    </div>
  );
}
