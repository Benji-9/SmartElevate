import { useEffect, useId, useRef, useState } from 'react';
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
  const questionId = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  // Foco pendiente de devolver al botón inicial cuando vuelve a renderizarse.
  const restoreFocus = useRef(false);

  useEffect(() => {
    if (!asking && restoreFocus.current) {
      restoreFocus.current = false;
      trigger.current?.focus();
    }
  }, [asking]);

  // Si al confirmar la fila desaparece, el foco no se pierde: va al contenido principal.
  useEffect(
    () => () => {
      if (restoreFocus.current) document.getElementById('main')?.focus();
    },
    [],
  );

  function close(confirmed: boolean) {
    restoreFocus.current = true;
    setAsking(false);
    if (confirmed) onConfirm();
  }

  if (!asking) {
    return (
      <Button
        ref={trigger}
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
    <div role="group" aria-labelledby={questionId} className="confirm-action">
      <p id={questionId}>{question}</p>
      <Button block={false} disabled={disabled} onClick={() => close(true)}>
        {confirmLabel}
      </Button>
      {/* El foco empieza en la opción segura. */}
      <Button variant="secondary" block={false} autoFocus onClick={() => close(false)}>
        Cancelar
      </Button>
    </div>
  );
}
