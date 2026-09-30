import type { CongestionLevel } from '../../types/pending';

/** Tono de `Chip` y etiqueta escrita de cada nivel de congestión. */
export const congestionTone = {
  LOW: 'baja',
  MEDIUM: 'media',
  HIGH: 'alta',
} as const satisfies Record<CongestionLevel, string>;

export const congestionLabel: Record<CongestionLevel, string> = {
  LOW: 'Baja',
  MEDIUM: 'Media',
  HIGH: 'Alta',
};
