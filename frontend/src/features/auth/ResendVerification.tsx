import { useEffect, useState, type FormEvent } from 'react';
import { Button } from '../../components/Button';
import { ApiError, networkErrorMessage, resendVerification } from '../../services/api';
import { UadeEmailInput } from './UadeEmailInput';
import { toUadeEmail, validateUadeUser } from './validation';

/** El servidor aplica su minuto de espera sin avisar (202), así que el botón la cuenta acá (#344). */
const RESEND_COOLDOWN_SECONDS = 60;

/**
 * Reenvío del link de verificación (SCREENS.md §02d). Con `email` lo reenvía directo; sin él
 * pide el email institucional. El único 429 es el tope de pedidos por IP (#344): trae
 * `Retry-After`, se muestra la espera y el botón vuelve cuando termina.
 */
/** Hasta un minuto en segundos; desde ahí, minutos redondeados hacia arriba. */
function waitLabel(seconds: number) {
  return seconds <= 60 ? `${seconds} s` : `${Math.ceil(seconds / 60)} min`;
}

export function ResendVerification({ email }: { email?: string }) {
  const [typed, setTyped] = useState('');
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);
  const [sending, setSending] = useState(false);
  const [wait, setWait] = useState(0);

  useEffect(() => {
    if (wait <= 0) return;
    const id = setTimeout(() => setWait((s) => s - 1), 1000);
    return () => clearTimeout(id);
  }, [wait]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const invalid = email ? undefined : validateUadeUser(typed);
    setFieldError(invalid);
    setMessage(null);
    if (invalid) return;

    const target = email ?? toUadeEmail(typed);
    setSending(true);
    try {
      await resendVerification({ email: target });
      setMessage({
        kind: 'ok',
        text: `Si ${target} tiene una cuenta sin verificar, te mandamos un link nuevo. Si no lo ves, mirá en spam.`,
      });
      setWait(RESEND_COOLDOWN_SECONDS);
    } catch (error) {
      if (error instanceof ApiError && error.status === 429) {
        setWait(error.retryAfter ?? RESEND_COOLDOWN_SECONDS);
        setMessage({
          kind: 'error',
          text: 'Hubo demasiados pedidos desde tu red. Probá de nuevo en un rato.',
        });
      } else {
        setMessage({
          kind: 'error',
          text: error instanceof ApiError ? error.message : networkErrorMessage(),
        });
      }
    } finally {
      setSending(false);
    }
  }

  return (
    <form className="auth__form" noValidate onSubmit={handleSubmit}>
      {!email && <UadeEmailInput value={typed} onChange={setTyped} error={fieldError} />}
      {message && (
        <p
          role={message.kind === 'ok' ? 'status' : 'alert'}
          className={message.kind === 'ok' ? 'auth__info' : 'auth__alert'}
        >
          {message.text}
        </p>
      )}
      <Button
        type="submit"
        variant="secondary"
        loading={sending}
        loadingLabel="Enviando…"
        disabled={wait > 0}
      >
        {wait > 0 ? `Podés pedir otro en ${waitLabel(wait)}` : 'Reenviar email de verificación'}
      </Button>
    </form>
  );
}
