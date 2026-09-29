import { TIME_ZONE } from '../turn/format';

const decimal = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });

const rangeFormat = new Intl.DateTimeFormat('es-AR', {
  timeZone: TIME_ZONE,
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

/** Espacio no separable: el número y su unidad no se cortan en dos líneas. */
export const NBSP = String.fromCharCode(0xa0);

/** `"1.234"`, `"6,8"`. */
export const formatNumber = (n: number) => decimal.format(n);

/** `"43,5 %"`. */
export const formatPercent = (n: number) => `${decimal.format(n)}${NBSP}%`;

/** `"3,5 min"` a partir de segundos. */
export const formatMinutes = (seconds: number) => `${decimal.format(seconds / 60)} min`;

/** `"29 de septiembre de 2026"` o `"23–29 de septiembre de 2026"`; `to` es exclusivo. */
export const formatPeriod = (from: string, to: string) =>
  rangeFormat.formatRange(new Date(from), new Date(Date.parse(to) - 1));
