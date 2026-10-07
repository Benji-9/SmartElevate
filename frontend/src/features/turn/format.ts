// Las franjas se muestran siempre en hora de Buenos Aires, aunque el dispositivo esté en otra zona.
export const TIME_ZONE = 'America/Argentina/Buenos_Aires';

const timeFormat = new Intl.DateTimeFormat('es-AR', {
  timeZone: TIME_ZONE,
  hour: 'numeric',
  minute: '2-digit',
  hourCycle: 'h23',
});

const dateFormat = new Intl.DateTimeFormat('es-AR', {
  timeZone: TIME_ZONE,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

const dayFormat = new Intl.DateTimeFormat('es-AR', {
  timeZone: TIME_ZONE,
  weekday: 'long',
  day: 'numeric',
  month: 'numeric',
});

/** `"7:25"`, `"14:32"` a partir de un instante ISO en UTC (es-AR pone "07:25": se saca el 0). */
export const formatTime = (iso: string) => timeFormat.format(new Date(iso)).replace(/^0(?=\d)/, '');

/** `"martes, 29 de septiembre"`: para usar dentro de una oración. */
export const formatDate = (date: Date) => dateFormat.format(date);

/** `"Martes 29/9"` (DESIGN-SYSTEM §8): la fecha suelta, como en el saludo del inicio. */
export function formatDay(date: Date) {
  const part = Object.fromEntries(dayFormat.formatToParts(date).map((p) => [p.type, p.value]));
  return `${part.weekday[0].toUpperCase()}${part.weekday.slice(1)} ${part.day}/${part.month}`;
}

/** `"14:32 – 14:34"`: la franja de una salida. */
export const formatSlot = (departsAt: string, durationMinutes: number) =>
  `${formatTime(departsAt)} – ${formatTime(
    new Date(Date.parse(departsAt) + durationMinutes * 60_000).toISOString(),
  )}`;

/** `"PB"`, `"3"`, `"-2"`: la planta baja se muestra como PB. */
export const formatFloor = (floor: number) => (floor === 0 ? 'PB' : String(floor));
