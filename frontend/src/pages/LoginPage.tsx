import { useId, useState, type FormEvent } from 'react';
import { flushSync } from 'react-dom';
import { Link, useLocation, useNavigate, type Location } from 'react-router';
import { Button } from '../components/Button';
import { Input } from '../components/Input';
import { LegalFooter } from '../components/LegalFooter';
import { ResendVerification } from '../features/auth/ResendVerification';
import { UadeEmailInput } from '../features/auth/UadeEmailInput';
import { focusFirstInvalid, toUadeEmail, validateUadeUser } from '../features/auth/validation';
import { useSession } from '../hooks/useSession';
import { ApiError, networkErrorMessage, SESSION_EXPIRED_MESSAGE } from '../services/api';
import logo from '../assets/smartelevate-logo-horizontal.svg';
import { Mascot } from '../components/Mascot';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import './AuthPages.css';

type Errors = { email?: string; password?: string };

export function LoginPage() {
  useDocumentTitle('Ingresar');
  const { login, expired } = useSession();
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: Location } | null)?.from;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [serverError, setServerError] = useState<string | null>(
    expired ? SESSION_EXPIRED_MESSAGE : null,
  );
  const [submitting, setSubmitting] = useState(false);
  const [unverifiedEmail, setUnverifiedEmail] = useState<string | null>(null);
  // 401: el error es de lo que se escribió, así que los dos campos quedan marcados.
  const [rejected, setRejected] = useState(false);
  const alertId = useId();
  const rejectedProps = rejected ? { 'aria-invalid': true, 'aria-describedby': alertId } : {};

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const next: Errors = {
      email: validateUadeUser(email),
      password: password ? undefined : 'Ingresá tu contraseña.',
    };
    flushSync(() => {
      setErrors(next);
      setServerError(null);
      setUnverifiedEmail(null);
      setRejected(false);
    });
    if (next.email || next.password) {
      focusFirstInvalid(form);
      return;
    }

    setSubmitting(true);
    try {
      await login({ email: toUadeEmail(email), password });
      navigate(from ?? '/', { replace: true });
    } catch (error) {
      flushSync(() => {
        // 403: la cuenta existe pero falta verificar el email.
        if (error instanceof ApiError && error.status === 403) {
          setUnverifiedEmail(toUadeEmail(email));
        }
        setRejected(error instanceof ApiError && error.status === 401);
        setServerError(error instanceof ApiError ? error.message : networkErrorMessage());
        setSubmitting(false);
      });
      focusFirstInvalid(form);
    }
  }

  return (
    <section className="auth auth--login auth-card">
      <header className="auth__intro">
        {/* La marca una sola vez (el h1 ya nombra a SmartElevate) y Elevy saludando. */}
        <img className="auth__logo" src={logo} alt="" width={131} height={32} />
        <Mascot pose="saludo" size={104} />
        <h1 className="text-title">Te damos la bienvenida a SmartElevate</h1>
        <p className="auth__subtitle text-body">
          Reservá tu turno de ascensor y llegá a tiempo a clase.
        </p>
      </header>

      <form className="auth__form" noValidate onSubmit={handleSubmit}>
        <UadeEmailInput value={email} onChange={setEmail} error={errors.email} {...rejectedProps} />
        <Input
          label="Contraseña"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
          {...rejectedProps}
        />
        <Link to="/recuperar" className="auth__text-button">
          ¿Olvidaste tu contraseña?
        </Link>
        {serverError && (
          <p id={alertId} role="alert" className="auth__alert">
            {serverError}
          </p>
        )}
        <Button type="submit" loading={submitting} loadingLabel="Ingresando…">
          Ingresar
        </Button>
      </form>
      {unverifiedEmail && <ResendVerification email={unverifiedEmail} />}

      <p className="auth__footer">
        ¿No tenés cuenta? <Link to="/registro">Registrate</Link>
      </p>

      <LegalFooter withUadeLogo />
    </section>
  );
}
