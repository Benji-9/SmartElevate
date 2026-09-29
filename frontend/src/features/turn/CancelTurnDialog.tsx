import { useEffect, useId, useRef } from 'react';
import { Button } from '../../components/Button';

type CancelTurnDialogProps = {
  open: boolean;
  /** Lo informa el servidor: si es `true`, avisamos antes de confirmar. */
  countsAsNoShow: boolean;
  cancelling: boolean;
  error: string | null;
  onConfirm: () => void;
  onClose: () => void;
};

/** Confirmación de cancelación con `<dialog>` modal nativo (foco atrapado y Escape). */
export function CancelTurnDialog({
  open,
  countsAsNoShow,
  cancelling,
  error,
  onConfirm,
  onClose,
}: CancelTurnDialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      className="turn-dialog"
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      onClose={onClose}
      // Mientras se cancela, Escape no cierra el diálogo.
      onCancel={(event) => {
        if (cancelling) event.preventDefault();
      }}
    >
      <h2 id={titleId}>¿Cancelar el turno?</h2>
      {countsAsNoShow ? (
        <p id={descriptionId} className="turn-dialog__warning">
          Ya pasó el límite para cancelar: si cancelás ahora, cuenta como falta.
        </p>
      ) : (
        <p id={descriptionId}>Vas a liberar tu lugar para que lo use otra persona.</p>
      )}
      {error && (
        <p role="alert" className="turn-alert">
          {error}
        </p>
      )}
      <div className="turn-dialog__actions">
        <Button loading={cancelling} loadingLabel="Cancelando…" onClick={onConfirm}>
          Sí, cancelar
        </Button>
        <Button variant="secondary" disabled={cancelling} onClick={onClose}>
          Volver
        </Button>
      </div>
    </dialog>
  );
}
