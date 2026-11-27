import { createContext, useContext } from 'react';
import type { LoginRequest, User } from '../types/api';

export type SessionState = {
  /** Usuario según `/users/me`; `null` sin sesión. */
  user: User | null;
  /** `true` mientras se recupera la sesión al abrir la app. */
  loading: boolean;
  /** La sesión venció (no la cerró la persona): el login lo explica. */
  expired: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
};

export const SessionContext = createContext<SessionState | null>(null);

export function useSession(): SessionState {
  const session = useContext(SessionContext);
  if (!session) throw new Error('useSession tiene que usarse dentro de <SessionProvider>');
  return session;
}
