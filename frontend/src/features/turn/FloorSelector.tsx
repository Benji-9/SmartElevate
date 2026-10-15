import { OptionGroup } from '../../components/OptionGroup';
import { formatFloor } from './format';

type FloorSelectorProps = {
  floors: number[];
  value: number | null;
  onChange: (floor: number) => void;
};

/**
 * Piso de destino entre los que sirve el ascensor desde el origen. Lo que depende del usuario
 * (pisos bajos, exención por movilidad reducida) lo valida el servidor al reservar.
 */
export function FloorSelector({ floors, value, onChange }: FloorSelectorProps) {
  return (
    <OptionGroup
      label="Piso de destino"
      name="destination-floor"
      options={floors.map((floor) => ({ value: String(floor), label: formatFloor(floor) }))}
      value={value === null ? null : String(value)}
      onChange={(floor) => onChange(Number(floor))}
      className="reserve__floors"
    />
  );
}
