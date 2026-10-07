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
      {/* Con falta, la consecuencia va en el título: es lo primero que se lee y anuncia (#151). */}
      <h2 id={titleId}>
        {countsAsNoShow ? 'Si cancelás ahora, cuenta como falta' : '¿Cancelar el turno?'}
      </h2>
      {countsAsNoShow ? (
        <p id={descriptionId} className="turn-dialog__warning">
          Ya pasó el límite para cancelar sin que cuente como falta.
        </p>
      ) : (
        <p id={descriptionId}>Vas a liberar tu lugar para que lo use otra persona.</p>
      )}
      {error && (
        <p role="alert" className="turn-alert">
          {error}
        </p>
      )}
      {/*
       * Mantener es la opción segura: va primero (recibe el foco al abrir) y con el énfasis
       * del botón primario; la acción destructiva queda como secundaria (#151).
       */}
      <div className="turn-dialog__actions">
        <Button disabled={cancelling} onClick={onClose}>
          Mantener turno
        </Button>
        <Button
          variant="secondary"
          loading={cancelling}
          loadingLabel="Cancelando…"
          onClick={onConfirm}
        >
          Cancelar turno
        </Button>
      </div>
    </dialog>
  );
}
