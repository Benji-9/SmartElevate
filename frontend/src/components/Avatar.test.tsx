import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Avatar } from './Avatar';

describe('Avatar', () => {
  it.each([
    ['Ana Pérez', 'AP'],
    ['  ana   pérez  gómez ', 'AP'],
    ['Ana', 'A'],
    ['', ''],
    ['   ', ''],
  ])('con "%s" muestra "%s"', (name, expected) => {
    const { container } = render(<Avatar name={name} />);
    expect(container.querySelector('.avatar')).toHaveTextContent(expected);
    expect(container.querySelector('.avatar')?.textContent).toBe(expected);
  });
});
