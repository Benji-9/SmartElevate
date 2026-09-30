import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, type Location } from 'react-router';
import { Button } from '../components/Button';
import { Input } from '../components/Input';
import { validateUadeEmail } from '../features/auth/validation';
import { useSession } from '../hooks/useSession';
import { ApiError } from '../services/api';
import logo from '../assets/smartelevate-logo.svg';
import uadeLogo from '../assets/uade-logo.svg';
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
  const [showForgotNotice, setShowForgotNotice] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: Errors = {
      email: validateUadeEmail(email),
      password: password ? undefined : 'Ingresá tu contraseña.',
    };
    setErrors(next);
    setServerError(null);
    if (next.email || next.password) return;

    setSubmitting(true);
    try {
      await login({ email: email.trim(), password });
      navigate(from ?? '/', { replace: true });
    } catch (error) {
      setServerError(
        error instanceof ApiError ? error.message : 'No pudimos conectarnos. Probá de nuevo.',
      );
      setSubmitting(false);
    }
  }

  return (
    <section className="auth">
      <header className="auth__intro">
        <img className="auth__logo" src={logo} alt="" width={181} height={120} />
        <h1>Bienvenido a SmartElevate</h1>
        <p className="auth__subtitle">Reservá tu turno de ascensor y llegá a tiempo a clase.</p>
      </header>

      <form className="auth__form" noValidate onSubmit={handleSubmit}>
        <Input
          label="Email institucional"
          type="email"
          autoComplete="username"
          placeholder="nombre@uade.edu.ar"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errors.email}
        />
        <Input
          label="Contraseña"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
        />
        <button
          type="button"
          className="auth__text-button"
          onClick={() => setShowForgotNotice(true)}
        >
          ¿Olvidaste tu contraseña?
        </button>
        {showForgotNotice && (
          <p role="status" className="auth__notice">
            La recuperación de contraseña todavía no está disponible.
          </p>
        )}
        {serverError && (
          <p role="alert" className="auth__alert">
            {serverError}
          </p>
        )}
        <Button type="submit" loading={submitting} loadingLabel="Ingresando…">
          Ingresar
        </Button>
      </form>

      <p className="auth__footer">
        ¿No tenés cuenta? <Link to="/registro">Registrate</Link>
      </p>

      <footer className="auth__org">
        <img className="auth__uade-logo" src={uadeLogo} alt="UADE" width={86} height={30} />
      </footer>
    </section>
  );
}
