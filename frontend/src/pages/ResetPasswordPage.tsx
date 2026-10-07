import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { Button } from '../components/Button';
import { Input } from '../components/Input';
import { LegalFooter } from '../components/LegalFooter';
import { ScreenHeader } from '../components/ScreenHeader';
import { ApiError, resetPassword } from '../services/api';
import './AuthPages.css';

type Errors = { newPassword?: string; repeat?: string };

/** Contraseña nueva desde el link del mail, `/recuperar/nueva?token=…` (SCREENS.md §01b). */
export function ResetPasswordPage() {
  const token = useSearchParams()[0].get('token');
  const [newPassword, setNewPassword] = useState('');
  const [repeat, setRepeat] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: Errors = {
      newPassword: newPassword ? undefined : 'Elegí una contraseña.',
      repeat: repeat === newPassword ? undefined : 'Las contraseñas no coinciden.',
    };
    setErrors(next);
    setServerError(null);
    if (next.newPassword || next.repeat) return;

    setSubmitting(true);
    try {
      await resetPassword({ token: token!, newPassword });
      setDone(true);
    } catch (error) {
      if (!(error instanceof ApiError)) {
        setServerError('No pudimos conectarnos. Probá de nuevo.');
      } else {
        // La política de contraseña la valida el servidor, igual que en el registro.
        const violation = error.violations.find((v) => v.field === 'newPassword');
        if (violation) setErrors({ newPassword: violation.message });
        else setServerError(error.message);
      }
    } finally {
      setSubmitting(false);
    }
  }

  let content;
  if (done) {
    content = (
      <div role="status" className="auth">
        <h2>Listo, cambiaste tu contraseña</h2>
        <p className="auth__lead">Ya podés ingresar con la contraseña nueva.</p>
        <Link to="/login">Ir a ingresar</Link>
      </div>
    );
  } else if (!token) {
    content = (
      <div role="alert" className="auth">
        <p className="auth__alert">Este link no es válido. Pedí uno nuevo.</p>
        <Link to="/recuperar">Pedir otro link</Link>
      </div>
    );
  } else {
    content = (
      <form className="auth__form" noValidate onSubmit={handleSubmit}>
        <Input
          label="Contraseña nueva"
          type="password"
          autoComplete="new-password"
          placeholder="••••••••"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          error={errors.newPassword}
        />
        <Input
          label="Repetí la contraseña"
          type="password"
          autoComplete="new-password"
          placeholder="••••••••"
          value={repeat}
          onChange={(e) => setRepeat(e.target.value)}
          error={errors.repeat}
        />
        {serverError && (
          <div className="auth__alert" role="alert">
            <p>{serverError}</p>
            <Link to="/recuperar">Pedir otro link</Link>
          </div>
        )}
        <Button type="submit" loading={submitting} loadingLabel="Guardando…">
          Guardar contraseña
        </Button>
      </form>
    );
  }

  return (
    <div className="auth-card auth-card--register">
      <ScreenHeader title="Contraseña nueva" backTo="/login" />
      {content}
      <LegalFooter />
    </div>
  );
}
