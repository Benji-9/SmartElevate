import './Avatar.css';

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join('');

/** Círculo con las iniciales. Decorativo: el nombre ya está escrito en la pantalla. */
export function Avatar({ name, className }: { name: string; className?: string }) {
  return (
    <span className={['avatar', className].filter(Boolean).join(' ')} aria-hidden="true">
      {initials(name)}
    </span>
  );
}
