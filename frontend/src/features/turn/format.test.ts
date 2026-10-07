import { describe, expect, it } from 'vitest';
import { formatDate, formatDay, formatFloor, formatSlot, formatTime } from './format';

describe('format', () => {
  it('muestra la hora en Buenos Aires (UTC−3) sin importar la zona del dispositivo', () => {
    expect(formatTime('2026-09-29T17:04:00Z')).toBe('14:04');
  });

  it('muestra la hora sin cero adelante, como en "7:25"', () => {
    expect(formatTime('2026-09-29T10:25:00Z')).toBe('7:25');
  });

  it('muestra el día como "Lunes 28/9", en Buenos Aires', () => {
    expect(formatDay(new Date('2026-09-28T15:00:00Z'))).toBe('Lunes 28/9');
    expect(formatDay(new Date('2026-09-30T01:00:00Z'))).toBe('Martes 29/9');
  });

  it('arma la franja con la duración que viene de la API', () => {
    expect(formatSlot('2026-09-29T17:04:00Z', 2)).toBe('14:04 – 14:06');
  });

  it('usa la fecha de Buenos Aires aunque en UTC ya sea otro día', () => {
    expect(formatDate(new Date('2026-09-30T01:00:00Z'))).toMatch(/29 de septiembre/);
  });

  it('muestra la planta baja como PB', () => {
    expect([formatFloor(0), formatFloor(-2), formatFloor(7)]).toEqual(['PB', '-2', '7']);
  });
});
