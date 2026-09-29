// Las franjas se muestran siempre en hora de Buenos Aires, aunque el dispositivo esté en otra zona.
export const TIME_ZONE = 'America/Argentina/Buenos_Aires';

const timeFormat = new Intl.DateTimeFormat('es-AR', {
  timeZone: TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

const dateFormat = new Intl.DateTimeFormat('es-AR', {
  timeZone: TIME_ZONE,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

/** `"14:32"` a partir de un instante ISO en UTC. */
export const formatTime = (iso: string) => timeFormat.format(new Date(iso));

/** `"martes, 29 de septiembre"`. */
export const formatDate = (date: Date) => dateFormat.format(date);

/** `"14:32 – 14:34"`: la franja de una salida. */
export const formatSlot = (departsAt: string, durationMinutes: number) =>
  `${formatTime(departsAt)} – ${formatTime(
    new Date(Date.parse(departsAt) + durationMinutes * 60_000).toISOString(),
  )}`;

/** `"PB"`, `"3"`, `"-2"`: la planta baja se muestra como PB. */
export const formatFloor = (floor: number) => (floor === 0 ? 'PB' : String(floor));
