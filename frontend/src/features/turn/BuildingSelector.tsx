import { OptionGroup } from '../../components/OptionGroup';
import type { Building } from '../../types/api';
import type { Core } from '../../types/pending';

type BuildingSelectorProps = {
  buildings: Building[];
  cores: Core[];
  value: string | null;
  onChange: (coreId: string) => void;
};

// ponytail: la abreviatura sale del nombre ("Independencia 1" → "Indep. 1", SCREENS.md §04).
// Si el contrato suma un nombre corto del núcleo, usar ese.
const shortName = (name: string) => name.replace(/\p{L}{9,}/u, (word) => `${word.slice(0, 5)}.`);

/** Núcleo de ascensores: pills en una fila con wrap, en el orden de los edificios. */
export function BuildingSelector({ buildings, cores, value, onChange }: BuildingSelectorProps) {
  // El nombre visible sale del contrato (GET /buildings); solo se ofrecen núcleos del catálogo de /cores.
  const options = buildings.flatMap((building) =>
    building.cores
      .filter((core) => cores.some((c) => c.id === core.code))
      .map((core) => ({ value: core.code, label: shortName(core.name) })),
  );
  return (
    <OptionGroup
      label="Edificio"
      name="core"
      className="reserve__cores"
      options={options}
      value={value}
      onChange={onChange}
    />
  );
}
