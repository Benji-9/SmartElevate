import elevy from '../assets/elevy.png';
import './Mascot.css';

/**
 * Elevy, la mascota de SmartElevate. Es decorativa (`alt=""`): el mensaje siempre está en el
 * texto de al lado. `size` es el alto en px; el ancho sale de la proporción de la imagen.
 */
export function Mascot({ size = 120, className }: { size?: number; className?: string }) {
  return (
    <img
      className={['mascot', className].filter(Boolean).join(' ')}
      src={elevy}
      alt=""
      height={size}
      width={Math.round(size * (320 / 462))}
    />
  );
}
