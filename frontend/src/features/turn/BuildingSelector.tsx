import { useId } from 'react';
import { OptionGroup } from '../../components/OptionGroup';
import type { CoreOption } from './catalog';

type BuildingSelectorProps = {
  cores: CoreOption[];
  value: string | null;
  onChange: (coreCode: string) => void;
};

/**
 * Núcleo de ascensores: pills en una fila con wrap, en el orden de los edificios. Con el nombre
 * completo ("Independencia 2"), el mismo que el resumen, Inicio y el turno.
 */
export function BuildingSelector({ cores, value, onChange }: BuildingSelectorProps) {
  const hintId = useId();
  return (
    <div>
      <OptionGroup
        label="Núcleo"
        name="core"
        className="reserve__cores"
        options={cores.map((core) => ({ value: core.code, label: core.name }))}
        value={value}
        onChange={onChange}
        describedBy={hintId}
      />
      <p id={hintId} className="reserve__missing">
        Un núcleo es un grupo de ascensores, por ejemplo {cores[0].name}.
      </p>
    </div>
  );
}
