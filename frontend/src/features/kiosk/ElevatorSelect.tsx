import { useId } from 'react';
import { Button } from '../../components/Button';
import { useResource } from '../../hooks/useResource';
import { getElevators } from '../../services/api';

/** `select` nativo "Ascensor" con los ascensores activos de `GET /elevators` (09c y 09d). */
export function ElevatorSelect({
  value,
  onChange,
}: {
  value: string;
  onChange: (code: string) => void;
}) {
  const id = useId();
  const elevators = useResource(getElevators);

  if (elevators.error) {
    return (
      <div role="alert" className="admin__error">
        <p>{elevators.error}</p>
        <Button variant="secondary" block={false} onClick={elevators.reload}>
          Reintentar
        </Button>
      </div>
    );
  }
  if (!elevators.data) {
    return (
      <p role="status" className="page-placeholder">
        Cargando ascensores…
      </p>
    );
  }
  const active = elevators.data.filter((e) => e.active);
  if (active.length === 0) return <p className="page-placeholder">No hay ascensores cargados.</p>;

  return (
    <div className="input">
      <label htmlFor={id} className="input__label text-label">
        Ascensor
      </label>
      <select
        id={id}
        className="input__field text-body"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">Elegí un ascensor</option>
        {active.map((e) => (
          <option key={e.code} value={e.code}>
            {e.code} · {e.coreName}
          </option>
        ))}
      </select>
    </div>
  );
}
