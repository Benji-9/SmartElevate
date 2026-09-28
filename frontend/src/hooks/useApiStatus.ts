import { useEffect, useState } from 'react';
import { ping } from '../services/api';

export type ApiStatus = 'checking' | 'online' | 'offline';

/** Consulta `GET /api/ping` una vez al montar para verificar la conexión front-back. */
export function useApiStatus(): ApiStatus {
  const [status, setStatus] = useState<ApiStatus>('checking');

  useEffect(() => {
    const controller = new AbortController();
    ping(controller.signal)
      .then((res) => setStatus(res.status === 'ok' ? 'online' : 'offline'))
      .catch(() => {
        if (!controller.signal.aborted) setStatus('offline');
      });
    return () => controller.abort();
  }, []);

  return status;
}
