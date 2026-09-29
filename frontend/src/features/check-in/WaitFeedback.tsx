import { useState, type FormEvent } from 'react';
import { Button } from '../../components/Button';
import { OptionGroup, type Option } from '../../components/OptionGroup';
import { ApiError, sendWaitFeedback } from '../../services/api';
import type { WaitRange } from '../../types/pending';

const ranges: Option<WaitRange>[] = [
  { value: 'UNDER_2', label: '< 2 min' },
  { value: 'FROM_2_TO_5', label: '2–5 min' },
  { value: 'FROM_5_TO_10', label: '5–10 min' },
  { value: 'OVER_10', label: '> 10 min' },
];

/** Encuesta opcional "¿Cuánto esperaste?": se responde una sola vez por check-in. */
export function WaitFeedback({ reservationId }: { reservationId: string }) {
  const [range, setRange] = useState<WaitRange | null>(null);
  const [sending, setSending] = useState(false);
  const [done, setDone] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!range || sending || done) return;
    setSending(true);
    setError(null);
    try {
      await sendWaitFeedback(reservationId, { range });
      setDone('¡Gracias! Registramos tu respuesta.');
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setDone('Ya respondiste esta encuesta.');
      } else {
        setError(e instanceof ApiError ? e.message : 'No pudimos conectarnos. Probá de nuevo.');
      }
    } finally {
      setSending(false);
    }
  }

  return (
    <form className="checkin-feedback" onSubmit={submit}>
      <OptionGroup
        label="¿Cuánto esperaste el ascensor?"
        name="wait-range"
        options={ranges.map((option) => ({ ...option, disabled: done !== null }))}
        value={range}
        onChange={setRange}
        layout="grid"
        columns={4}
      />
      <p className="checkin-feedback__hint">Nos ayuda a medir la congestión real.</p>
      {done ? (
        <p role="status" className="turn-hint">
          {done}
        </p>
      ) : (
        <Button
          type="submit"
          variant="secondary"
          disabled={!range}
          loading={sending}
          loadingLabel="Enviando…"
        >
          Enviar respuesta
        </Button>
      )}
      {error && (
        <p role="alert" className="turn-alert">
          {error}
        </p>
      )}
    </form>
  );
}
