import { useId } from 'react';
import { OptionGroup } from '../../components/OptionGroup';
import { formatFloor } from './format';

type FloorSelectorProps = {
  floors: number[];
  value: number | null;
  onChange: (floor: number) => void;
  /** Pisos que no se pueden elegir (van por escalera) y el motivo, que se muestra bajo la grilla. */
  blocked?: (floor: number) => boolean;
  blockedReason?: string;
};

/**
 * Piso de destino entre los que sirve el ascensor desde el origen. Los pisos bajos se deshabilitan con su
 * motivo; el servidor igual los valida al reservar.
 */
export function FloorSelector({
  floors,
  value,
  onChange,
  blocked = () => false,
  blockedReason,
}: FloorSelectorProps) {
  const reasonId = useId();
  const hasBlocked = floors.some(blocked);
  return (
    <div className="reserve__floors-group">
      <OptionGroup
        label="Piso de destino"
        name="destination-floor"
        options={floors.map((floor) => ({
          value: String(floor),
          label: formatFloor(floor),
          disabled: blocked(floor),
        }))}
        value={value === null ? null : String(value)}
        onChange={(floor) => onChange(Number(floor))}
        className="reserve__floors"
      />
      {hasBlocked && blockedReason && (
        <p id={reasonId} className="reserve__missing">
          {blockedReason}
        </p>
      )}
    </div>
  );
}
