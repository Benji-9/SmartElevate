import { OptionGroup } from '../../components/OptionGroup';
import type { Building, Core } from '../../types/pending';

type BuildingSelectorProps = {
  buildings: Building[];
  cores: Core[];
  value: string | null;
  onChange: (coreId: string) => void;
};

/** Núcleo de ascensores, agrupado por edificio. */
export function BuildingSelector({ buildings, cores, value, onChange }: BuildingSelectorProps) {
  return (
    <fieldset className="reserve__fieldset">
      <legend className="reserve__legend">Edificio y núcleo</legend>
      {buildings.map((building) => {
        const options = cores
          .filter((core) => core.buildingId === building.id)
          .map((core) => ({ value: core.id, label: core.name }));
        return (
          options.length > 0 && (
            <OptionGroup
              key={building.id}
              label={building.name}
              name={`core-${building.id}`}
              options={options}
              value={value}
              onChange={onChange}
            />
          )
        );
      })}
    </fieldset>
  );
}
