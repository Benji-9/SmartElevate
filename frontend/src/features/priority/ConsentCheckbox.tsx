type ConsentCheckboxProps = {
  checked: boolean;
  onChange: (checked: boolean) => void;
};

/** Consentimiento expreso para tratar el certificado, que es un dato de salud (Ley 25.326). */
export function ConsentCheckbox({ checked, onChange }: ConsentCheckboxProps) {
  return (
    <label className="consent-checkbox">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>
        Acepto que SmartElevate use mi certificado solo para validar el acceso prioritario. Es un
        dato de salud (Ley 25.326): se guarda de forma privada, lo ve solo el equipo revisor y se
        borra cuando se resuelve la solicitud.
      </span>
    </label>
  );
}
