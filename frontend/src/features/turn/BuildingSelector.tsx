import { useId } from 'react';
import { OptionGroup } from '../../components/OptionGroup';
import type { CoreOption } from './catalog';

type BuildingSelectorProps = {
  cores: CoreOption[];
  value: string | null;
  onChange: (coreCode: string) => void;
};

// ponytail: la abreviatura sale del nombre ("Independencia 1" → "Indep. 1", SCREENS.md §04).
// Si el contrato suma un nombre corto del núcleo, usar ese.
const shortName = (name: string) => name.replace(/\p{L}{9,}/u, (word) => `${word.slice(0, 5)}.`);

/** Núcleo de ascensores: pills en una fila con wrap, en el orden de los edificios. */
export function BuildingSelector({ cores, value, onChange }: BuildingSelectorProps) {
  const hintId = useId();
  return (
    <div>
      <OptionGroup
        label="Núcleo"
        name="core"
        className="reserve__cores"
        options={cores.map((core) => ({ value: core.code, label: shortName(core.name) }))}
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
