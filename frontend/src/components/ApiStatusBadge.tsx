import { useApiStatus } from '../hooks/useApiStatus';

const labels = {
  checking: 'Verificando conexión con la API…',
  online: 'API conectada',
  offline: 'API no disponible',
} as const;

export function ApiStatusBadge() {
  const status = useApiStatus();
  return (
    <p className={`api-status api-status--${status}`} role="status">
      {labels[status]}
    </p>
  );
}
