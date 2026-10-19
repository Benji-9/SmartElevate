import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, type Location } from 'react-router';
import { Button } from '../components/Button';
import { Input } from '../components/Input';
import { LegalFooter } from '../components/LegalFooter';
import { ResendVerification } from '../features/auth/ResendVerification';
import { UadeEmailInput } from '../features/auth/UadeEmailInput';
import { toUadeEmail, validateUadeUser } from '../features/auth/validation';
import { useSession } from '../hooks/useSession';
import { ApiError } from '../services/api';
import logo from '../assets/smartelevate-logo.svg';
import './AuthPages.css';

type Errors = { email?: string; password?: string };

export function LoginPage() {
  const { login } = useSession();
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: Location } | null)?.from;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [unverifiedEmail, setUnverifiedEmail] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: Errors = {
      email: validateUadeUser(email),
      password: password ? undefined : 'Ingresá tu contraseña.',
    };
    setErrors(next);
    setServerError(null);
    setUnverifiedEmail(null);
    if (next.email || next.password) return;

    setSubmitting(true);
    try {
      await login({ email: toUadeEmail(email), password });
      navigate(from ?? '/', { replace: true });
    } catch (error) {
      // 403: la cuenta existe pero falta verificar el email.
      if (error instanceof ApiError && error.status === 403) {
        setUnverifiedEmail(toUadeEmail(email));
      }
      setServerError(
        error instanceof ApiError ? error.message : 'No pudimos conectarnos. Probá de nuevo.',
      );
      setSubmitting(false);
    }
  }

  return (
    <section className="auth auth--login auth-card">
      <header className="auth__intro">
        <img className="auth__logo" src={logo} alt="" width={181} height={120} />
        <h1 className="text-title">Te damos la bienvenida a SmartElevate</h1>
        <p className="auth__subtitle text-body">
          Reservá tu turno de ascensor y llegá a tiempo a clase.
        </p>
      </header>

      <form className="auth__form" noValidate onSubmit={handleSubmit}>
        <UadeEmailInput value={email} onChange={setEmail} error={errors.email} />
        <Input
          label="Contraseña"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
        />
        <Link to="/recuperar" className="auth__text-button">
          ¿Olvidaste tu contraseña?
        </Link>
        {serverError && (
          <p role="alert" className="auth__alert">
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
