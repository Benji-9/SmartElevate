import { useState, type FormEvent } from 'react';
import { Button } from '../../components/Button';
import { Chip } from '../../components/Chip';
import { errorMessage, useResource } from '../../hooks/useResource';
import {
  getMyPriorityRequest,
  getPriorityUploadRules,
  submitPriorityRequest,
} from '../../services/api';
import type { PriorityRequest, PriorityUploadRules } from '../../types/pending';
import { TIME_ZONE } from '../turn/format';
import { ConsentCheckbox } from './ConsentCheckbox';
import { UploadDropzone } from './UploadDropzone';
import { UploadedFile } from './UploadedFile';
import './priority.css';

// `DD/MM`: es-AR no rellena el día con cero ("5/10"); en-GB sí ("05/10").
const dayMonth = new Intl.DateTimeFormat('en-GB', {
  timeZone: TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
});

const load = () => Promise.all([getMyPriorityRequest(), getPriorityUploadRules()]);

type Status = { label: string; tone: 'primary' | 'neutral' | 'danger'; text: string };

function describe(request: PriorityRequest | null): Status & { canUpload: boolean } {
  if (request?.status === 'PENDING') {
    return {
      label: 'Pendiente de validación',
      tone: 'primary',
      text: 'Estamos revisando tu certificado. Te avisamos cuando se resuelva.',
      canUpload: false,
    };
  }
  if (request?.status === 'APPROVED') {
    const expiresAt = request.expiresAt;
    if (expiresAt && Date.parse(expiresAt) <= Date.now()) {
      return {
        label: `Venció el ${dayMonth.format(new Date(expiresAt))}`,
        tone: 'neutral',
        text: 'Tu acceso prioritario venció. Subí un certificado vigente para renovarlo.',
        canUpload: true,
      };
    }
    return {
      label: expiresAt ? `Aprobado (vence el ${dayMonth.format(new Date(expiresAt))})` : 'Aprobado',
      tone: 'primary',
      text: 'Tenés lugares reservados en cada salida del ascensor.',
      canUpload: false,
    };
  }
  if (request?.status === 'REJECTED') {
    return {
      label: 'Rechazado',
      tone: 'danger',
      text: 'No pudimos validar tu certificado. Podés enviar uno nuevo.',
      canUpload: true,
    };
  }
  return {
    label: 'Sin solicitud',
    tone: 'neutral',
    text: 'Si tenés movilidad reducida, subí tu certificado para tener lugares reservados en el ascensor.',
    canUpload: true,
  };
}

/** Estado del acceso prioritario y, si corresponde, el formulario para pedirlo. */
export function PriorityAccessCard() {
  const { data, error, loading, reload } = useResource(load);
  // Lo que devolvió el envío: pisa lo cargado sin volver a pedirlo.
  const [submitted, setSubmitted] = useState<PriorityRequest | null>(null);

  let content;
  if (loading) {
    content = (
      <p role="status" className="page-placeholder">
        Cargando tu solicitud…
      </p>
    );
  } else if (error || !data) {
    content = (
      <>
        <p role="alert" className="priority-error">
          {error}
        </p>
        <Button variant="secondary" onClick={reload}>
          Reintentar
        </Button>
      </>
    );
  } else {
    const [request, rules] = data;
    const status = describe(submitted ?? request);
    content = (
      <>
        <Chip tone={status.tone} className="priority-card__chip">
          {status.label}
        </Chip>
        <p className="priority-card__text">{status.text}</p>
        {submitted && (
          <p role="status" className="priority-card__notice">
            Enviamos tu solicitud.
          </p>
        )}
        {status.canUpload && <PriorityRequestForm rules={rules} onSubmitted={setSubmitted} />}
      </>
    );
  }

  return (
    <section className="priority-card" aria-labelledby="priority-title">
      <h2 id="priority-title">Acceso prioritario</h2>
      {content}
    </section>
  );
}

type FormProps = {
  rules: PriorityUploadRules;
  onSubmitted: (request: PriorityRequest) => void;
};

function PriorityRequestForm({ rules, onSubmitted }: FormProps) {
  const [file, setFile] = useState<File | null>(null);
  const [consent, setConsent] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // El certificado es un dato de salud: nunca se loguea ni se guarda fuera de este estado.
  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!file || !consent) return;
    setSending(true);
    setError(null);
    try {
      onSubmitted(await submitPriorityRequest(file, true));
    } catch (err) {
      setError(errorMessage(err));
      setSending(false);
    }
  }

  return (
    <form className="priority-form" onSubmit={handleSubmit}>
      {file ? (
        <UploadedFile file={file} onRemove={() => setFile(null)} disabled={sending} />
      ) : (
        <UploadDropzone rules={rules} onSelect={setFile} />
      )}
      <ConsentCheckbox checked={consent} onChange={setConsent} />
      {error && (
        <p role="alert" className="priority-error">
          {error}
        </p>
      )}
      <Button type="submit" disabled={!file || !consent} loading={sending} loadingLabel="Enviando…">
        Enviar solicitud
      </Button>
    </form>
  );
}
