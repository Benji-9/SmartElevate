import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { LegalFooter } from '../components/LegalFooter';
import { ScreenHeader } from '../components/ScreenHeader';
import { ResendVerification } from '../features/auth/ResendVerification';
import { ApiError, verifyEmail } from '../services/api';
import './AuthPages.css';

type State = { status: 'loading' } | { status: 'ok' } | { status: 'error'; message: string };

/** Verifica el email con el token del link, `/verificar-email?token=…` (SCREENS.md §02d). */
export function VerifyEmailPage() {
  const token = useSearchParams()[0].get('token');
  const [state, setState] = useState<State>(
    token
      ? { status: 'loading' }
      : { status: 'error', message: 'Este link no es válido. Pedí uno nuevo.' },
  );

  useEffect(() => {
    if (!token) return;
    // Es POST a propósito: los antivirus que abren el mail no gastan el token.
    verifyEmail({ token }).then(
      () => setState({ status: 'ok' }),
      (error) =>
        setState({
          status: 'error',
          message:
            error instanceof ApiError ? error.message : 'No pudimos conectarnos. Probá de nuevo.',
        }),
    );
  }, [token]);

  return (
    <div className="auth-card auth-card--register">
      <ScreenHeader title="Verificar email" backTo="/login" />
      {state.status === 'loading' && (
        <p role="status" className="auth__lead">
          Verificando tu email…
        </p>
      )}
      {state.status === 'ok' && (
        <div role="status" className="auth">
          <h2>Listo, verificamos tu email</h2>
          <p className="auth__lead">Ya podés ingresar a SmartElevate.</p>
          <Link to="/login">Ir a ingresar</Link>
        </div>
      )}
      {state.status === 'error' && (
        <div className="auth">
          <p role="alert" className="auth__alert">
            {state.message}
          </p>
          <p className="auth__lead">Pedí un link nuevo a tu email institucional.</p>
          <ResendVerification />
        </div>
      )}
      <LegalFooter />
    </div>
  );
}
