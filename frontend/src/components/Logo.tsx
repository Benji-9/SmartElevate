/** Marca de SmartElevate: ascensor con flechas de subida y bajada. Decorativa (el nombre va en texto). */
export function Logo({ size = 64 }: { size?: number }) {
  return (
    <svg viewBox="0 0 64 64" width={size} height={size} aria-hidden="true" className="logo">
      <rect width="64" height="64" rx="18" fill="var(--color-primary)" />
      <rect
        x="18"
        y="14"
        width="28"
        height="36"
        rx="4"
        fill="none"
        stroke="var(--color-on-primary)"
        strokeWidth="3"
      />
      <path
        d="M26 28l6-6 6 6M26 36l6 6 6-6"
        fill="none"
        stroke="var(--color-on-primary)"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
