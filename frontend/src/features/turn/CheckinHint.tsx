import { QR_INSTRUCTION } from '../check-in/qrInstruction';

/** Recordatorio de hacer check-in al llegar al hall. */
export function CheckinHint() {
  return <p className="turn-hint">{QR_INSTRUCTION}</p>;
}
