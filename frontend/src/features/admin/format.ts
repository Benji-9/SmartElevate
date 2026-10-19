import type { AdminShift } from '../../types/api';
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

/** Lo que se muestra en lugar de un KPI que el servidor manda en `null`. */
export const NO_DATA = 'Sin datos';

/** `"3,5 min"` a partir de segundos. */
export const formatMinutes = (seconds: number) => `${decimal.format(seconds / 60)} min`;

/** `"29 de septiembre de 2026"` o `"23–29 de septiembre de 2026"`; `to` es exclusivo. */
export const formatPeriod = (from: string, to: string) =>
  rangeFormat.formatRange(new Date(from), new Date(Date.parse(to) - 1));

/** `"Turno mañana (7:00–12:15)"` a partir de las horas `"HH:mm"` que manda el servidor. */
export const formatShift = ({ name, startsAt, endsAt }: AdminShift) =>
  `${name} (${startsAt.replace(/^0/, '')}–${endsAt.replace(/^0/, '')})`;

const campusClock = new Intl.DateTimeFormat('en-GB', {
  timeZone: TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** El turno en curso a esta hora de Buenos Aires (`"HH:mm"` de texto compara bien; fin exclusivo). */
export function currentShift(shifts: AdminShift[], now: Date): AdminShift | undefined {
  const time = campusClock.format(now);
  return shifts.find((s) => s.startsAt <= time && time < s.endsAt);
}

const dayFormat = new Intl.DateTimeFormat('es-AR', { timeZone: TIME_ZONE });

const dateTimeFormat = new Intl.DateTimeFormat('es-AR', {
  timeZone: TIME_ZONE,
  day: 'numeric',
  month: 'numeric',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** `"14/10/2026, 15:40"` en hora de Buenos Aires. */
export const formatDateTime = (iso: string) => dateTimeFormat.format(new Date(iso));

/** `"29/9/2026"` en hora de Buenos Aires. */
export const formatRegisteredAt = (iso: string) => dayFormat.format(new Date(iso));
