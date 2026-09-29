import { ScreenHeader } from '../components/ScreenHeader';

export function CheckInCodePage() {
  return (
    <>
      <ScreenHeader title="Ingresar código" backTo="/check-in" />
      <p className="page-placeholder">
        Próximamente: tipeá el código que aparece debajo del QR del ascensor.
      </p>
    </>
  );
}
