import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { SessionContext, type SessionState } from '../../hooks/useSession';
import * as api from '../../services/api';
import type { LoginRequest, User } from '../../types/api';

/** Sesión del usuario: la recupera al abrir la app y la limpia si vence o al cerrar sesión. */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    let active = true;
    api
      .restoreSession()
      .catch(() => null)
      .then((restored) => {
        if (!active) return;
        setUser(restored);
        setLoading(false);
      });
    const unsubscribe = api.onSessionExpired(() => {
      setUser(null);
      setExpired(true);
    });
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  const login = useCallback(async (credentials: LoginRequest) => {
    setUser(await api.login(credentials));
    setExpired(false);
  }, []);

  const logout = useCallback(async () => {
    await api.logout();
    setUser(null);
    setExpired(false);
  }, []);

  const value = useMemo<SessionState>(
    () => ({ user, loading, expired, login, logout }),
    [user, loading, expired, login, logout],
  );

  return <SessionContext value={value}>{children}</SessionContext>;
}
