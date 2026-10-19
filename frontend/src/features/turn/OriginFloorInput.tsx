import { OptionGroup } from '../../components/OptionGroup';
import { formatFloor } from './format';

type OriginFloorInputProps = {
  floors: number[];
  value: number | null;
  onChange: (floor: number) => void;
};

/** Piso donde estás ahora: la misma grilla de botones que el destino. */
export function OriginFloorInput({ floors, value, onChange }: OriginFloorInputProps) {
  return (
    <OptionGroup
      label="Piso de origen"
      name="origin-floor"
      options={floors.map((floor) => ({ value: String(floor), label: formatFloor(floor) }))}
      value={value === null ? null : String(value)}
      onChange={(floor) => onChange(Number(floor))}
      className="reserve__floors"
    />
  );
}
