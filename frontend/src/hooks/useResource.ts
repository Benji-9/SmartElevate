import { useEffect, useState } from 'react';
import { ApiError } from '../services/api';

type Result<T> = { load: () => Promise<T>; version: number; data?: T; error?: string };

/** Mensaje para mostrar de un error de la API (o de red). */
export const errorMessage = (error: unknown) =>
  error instanceof ApiError ? error.message : 'No pudimos conectarnos. Probá de nuevo.';

/**
 * Trae datos con `load` (memoizada: si cambia, vuelve a pedir). Con `load = null` no pide nada.
 * Mientras recarga (`reload`) sigue devolviendo los datos anteriores.
 */
export function useResource<T>(load: (() => Promise<T>) | null) {
  const [version, setVersion] = useState(0);
  const [result, setResult] = useState<Result<T> | null>(null);

  useEffect(() => {
    if (!load) return;
    let ignore = false;
    load().then(
      (data) => !ignore && setResult({ load, version, data }),
      (error: unknown) => !ignore && setResult({ load, version, error: errorMessage(error) }),
    );
    return () => {
      ignore = true;
    };
  }, [load, version]);

  const current = load && result?.load === load ? result : null;
  return {
    data: current?.data,
    error: current?.error,
    loading: load !== null && current?.version !== version,
    reload: () => setVersion((v) => v + 1),
  };
}
