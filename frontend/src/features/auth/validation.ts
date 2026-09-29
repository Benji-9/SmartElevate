const UADE_EMAIL = /^[^\s@]+@uade\.edu\.ar$/i;

/** Mensaje de error del email institucional, o `undefined` si es válido. */
export function validateUadeEmail(value: string): string | undefined {
  if (!value.trim()) return 'Ingresá tu email institucional.';
  if (!UADE_EMAIL.test(value.trim())) return 'Usá tu email @uade.edu.ar.';
  return undefined;
}
