import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { CongestionLevel } from '../types/pending';
import { CongestionRow } from './CongestionRow';

describe('CongestionRow', () => {
  it.each<[CongestionLevel, string]>([
    ['LOW', 'Baja'],
    ['MEDIUM', 'Media'],
    ['HIGH', 'Alta'],
  ])('muestra el núcleo y el nivel %s escrito', (level, label) => {
    render(<CongestionRow label="Lima 1" level={level} value={0.5} />);
    expect(screen.getByText('Lima 1')).toBeInTheDocument();
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it('muestra el texto secundario si lo recibe', () => {
    render(<CongestionRow label="Lima 1" level="HIGH" value={1} detail="~9 min de espera" />);
    expect(screen.getByText('~9 min de espera')).toBeInTheDocument();
  });
});
