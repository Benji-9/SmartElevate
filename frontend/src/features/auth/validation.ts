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

/**
 * Política de contraseña: mismos límites y mensajes que `PasswordPolicy` del backend (ADR 0015).
 * Se cuentan caracteres (code points) tras normalizar con NFKC, no bytes: una ñ o un emoji valen 1.
 * La lista de contraseñas comunes la revisa solo el servidor; su error llega como violación del campo.
 */
export const PASSWORD_HINT = 'Entre 8 y 64 caracteres. No exigimos símbolos ni mayúsculas.';

export function validatePassword(value: string): string | undefined {
  const length = [...value.normalize('NFKC')].length;
  if (length < 8 || length > 64) {
    return 'La contraseña tiene que tener entre 8 y 64 caracteres.';
  }
  return undefined;
}
