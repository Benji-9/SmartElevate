/** Único dominio aceptado. El servidor lo vuelve a validar: esto es solo ayuda de UI. */
export const UADE_DOMAIN = '@uade.edu.ar';

/**
 * Lo que queda en el campo de usuario: si se pega o autocompleta el email completo con el
 * dominio de UADE, se saca el dominio para no terminar con `…@uade.edu.ar@uade.edu.ar`.
 */
export function toUadeUser(value: string): string {
  const trimmed = value.trim();
  return trimmed.toLowerCase().endsWith(UADE_DOMAIN)
    ? trimmed.slice(0, -UADE_DOMAIN.length)
    : value;
}

export const toUadeEmail = (user: string) => `${user.trim()}${UADE_DOMAIN}`;

/** Mensaje de error del usuario del email institucional, o `undefined` si es válido. */
export function validateUadeUser(value: string): string | undefined {
  const user = value.trim();
  if (!user) return 'Ingresá tu email institucional.';
  if (user.includes('@')) return `Usá tu email ${UADE_DOMAIN}.`;
  if (/\s/.test(user)) return 'Escribilo sin espacios.';
  return undefined;
}
