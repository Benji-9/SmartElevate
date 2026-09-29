import { createContext, useContext } from 'react';
import type { LoginRequest, User } from '../types/pending';

export type SessionState = {
  /** Usuario según `/me`; `null` sin sesión. */
  user: User | null;
  /** `true` mientras se recupera la sesión al abrir la app. */
  loading: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
};

export const SessionContext = createContext<SessionState | null>(null);

export function useSession(): SessionState {
  const session = useContext(SessionContext);
  if (!session) throw new Error('useSession tiene que usarse dentro de <SessionProvider>');
  return session;
}
