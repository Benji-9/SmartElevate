import { useId } from 'react';
import '../../components/Input.css';
import { formatFloor } from './format';

type OriginFloorInputProps = {
  floors: number[];
  value: number | null;
  onChange: (floor: number) => void;
};

/** Piso donde estás ahora (desplegable nativo). */
export function OriginFloorInput({ floors, value, onChange }: OriginFloorInputProps) {
  const id = useId();
  return (
    <div className="input">
      <label htmlFor={id} className="input__label">
        Piso de origen
      </label>
      <select
        id={id}
        className="input__field"
        value={value ?? ''}
        onChange={(e) => onChange(Number(e.target.value))}
      >
        <option value="" disabled>
          Elegí tu piso
        </option>
        {floors.map((floor) => (
          <option key={floor} value={floor}>
            {formatFloor(floor)}
          </option>
        ))}
      </select>
    </div>
  );
}
