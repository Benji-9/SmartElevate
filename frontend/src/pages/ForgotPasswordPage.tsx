import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { Button } from '../components/Button';
import { Input } from '../components/Input';
import { ScreenHeader } from '../components/ScreenHeader';
import { validateUadeEmail } from '../features/auth/validation';
import { ApiError, requestPasswordReset } from '../services/api';
import './AuthPages.css';

/** Pide el link para elegir otra contraseña (SCREENS.md §01b). */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | undefined>();
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const invalid = validateUadeEmail(email);
    setError(invalid);
    setServerError(null);
    if (invalid) return;

    setSubmitting(true);
    try {
      await requestPasswordReset({ email: email.trim() });
      setSentTo(email.trim());
    } catch (e) {
      setServerError(e instanceof ApiError ? e.message : 'No pudimos conectarnos. Probá de nuevo.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-card auth-card--register">
      <ScreenHeader title="Recuperar contraseña" backTo="/login" />
      {sentTo ? (
        // El servidor responde igual exista o no la cuenta: el texto no confirma nada.
        <div role="status" className="auth">
          <h2>Revisá tu email</h2>
          <p className="auth__lead">
            Si <strong>{sentTo}</strong> tiene una cuenta, te mandamos un link para elegir una
            contraseña nueva. Vence en unos minutos y sirve una sola vez.
          </p>
          <Link to="/login">Volver a ingresar</Link>
        </div>
      ) : (
        <>
          <p className="auth__lead">
            Te mandamos un link a tu email institucional para que elijas una contraseña nueva.
          </p>
          <form className="auth__form" noValidate onSubmit={handleSubmit}>
            <Input
              label="Email institucional"
              type="email"
              autoComplete="username"
              placeholder="nombre@uade.edu.ar"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={error}
            />
            {serverError && (
              <p role="alert" className="auth__alert">
                {serverError}
              </p>
            )}
            <Button type="submit" loading={submitting} loadingLabel="Enviando…">
              Enviar link
            </Button>
          </form>
        </>
      )}
    </div>
  );
}
