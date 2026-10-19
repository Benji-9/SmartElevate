import { useEffect, useState } from 'react';

/** Ahora (ms epoch), refrescado cada `intervalMs`: para pantallas que dependen de la hora. */
export function useNow(intervalMs: number) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}
