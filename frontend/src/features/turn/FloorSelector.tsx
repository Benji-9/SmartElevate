import { OptionGroup } from '../../components/OptionGroup';
import type { FloorOption } from '../../types/pending';
import { formatFloor } from './format';

type FloorSelectorProps = {
  floors: FloorOption[];
  value: number | null;
  onChange: (floor: number) => void;
};

/** Piso de destino: los no elegibles (según el servidor) quedan deshabilitados. */
export function FloorSelector({ floors, value, onChange }: FloorSelectorProps) {
  const reasons = [...new Set(floors.flatMap((f) => (!f.eligible && f.reason ? [f.reason] : [])))];
  return (
    <>
      <OptionGroup
        label="Piso de destino"
        name="destination-floor"
        options={floors.map((f) => ({
          value: String(f.floor),
          label: formatFloor(f.floor),
          disabled: !f.eligible,
        }))}
        value={value === null ? null : String(value)}
        onChange={(floor) => onChange(Number(floor))}
        layout="grid"
        columns={5}
      />
      <FloorRuleNotice reasons={reasons} />
    </>
  );
}

/** Por qué hay pisos deshabilitados (regla de pisos bajos, origen). El texto viene del servidor. */
export function FloorRuleNotice({ reasons }: { reasons: string[] }) {
  if (!reasons.length) return null;
  return (
    <div className="reserve__notice">
      <p className="reserve__notice-title">Algunos pisos no se pueden elegir</p>
      <ul>
        {reasons.map((reason) => (
          <li key={reason}>{reason}</li>
        ))}
      </ul>
    </div>
  );
}
