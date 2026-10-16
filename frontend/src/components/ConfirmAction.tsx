import { useState } from 'react';
import { Button } from './Button';
import './ConfirmAction.css';

/** Botón que pide confirmación en el lugar antes de ejecutar (rechazar, revocar, borrar). */
export function ConfirmAction({
  label,
  name,
  question,
  confirmLabel,
  onConfirm,
  disabled = false,
}: {
  label: string;
  /** Nombre accesible completo ("Rechazar a Ana"). */
  name?: string;
  question: string;
  confirmLabel: string;
  onConfirm: () => void;
  /** Deshabilita las acciones (p. ej. mientras otro pedido está en curso). */
  disabled?: boolean;
}) {
  const [asking, setAsking] = useState(false);
  if (!asking) {
    return (
      <Button
        variant="secondary"
        block={false}
        aria-label={name}
        disabled={disabled}
        onClick={() => setAsking(true)}
      >
        {label}
      </Button>
    );
  }
  return (
    <div role="group" aria-label={question} className="confirm-action">
      <p>{question}</p>
      <Button
        block={false}
        disabled={disabled}
        onClick={() => {
          setAsking(false);
          onConfirm();
        }}
      >
        {confirmLabel}
      </Button>
      {/* El foco empieza en la opción segura. */}
      <Button variant="secondary" block={false} autoFocus onClick={() => setAsking(false)}>
        Cancelar
      </Button>
    </div>
  );
}
