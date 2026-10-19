import { Mascot } from '../../components/Mascot';
import { QR_INSTRUCTION } from '../check-in/qrInstruction';

/** Recordatorio de hacer check-in al llegar al hall, con la cara de Elevy. */
export function CheckinHint() {
  return (
    <p className="turn-hint turn-hint--mascot">
      <Mascot pose="cara" size={32} />
      {QR_INSTRUCTION}
    </p>
  );
}
