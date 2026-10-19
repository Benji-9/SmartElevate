import cara from '../assets/elevy/cara.svg';
import duerme from '../assets/elevy/duerme.svg';
import festejo from '../assets/elevy/festejo.svg';
import piensa from '../assets/elevy/piensa.svg';
import saludo from '../assets/elevy/saludo.svg';
import senala from '../assets/elevy/senala.svg';
import triste from '../assets/elevy/triste.svg';
import './Mascot.css';

/** Imagen y proporción (ancho / alto) de cada pose. */
const poses = {
  saludo: { src: saludo, ratio: 200 / 230 },
  festejo: { src: festejo, ratio: 200 / 230 },
  senala: { src: senala, ratio: 200 / 230 },
  piensa: { src: piensa, ratio: 200 / 230 },
  triste: { src: triste, ratio: 200 / 230 },
  duerme: { src: duerme, ratio: 200 / 230 },
  cara: { src: cara, ratio: 1 },
} as const;

export type MascotPose = keyof typeof poses;

/**
 * Elevy, la mascota de SmartElevate. Es decorativa (`alt=""`): el mensaje siempre está en el
 * texto de al lado. `size` es el alto en px; el ancho sale de la proporción de la pose.
 * Cuerpo caricaturesco y cabeza inspirada en el robot G1 de UADE (DESIGN-SYSTEM.md §9).
 */
export function Mascot({
  pose = 'saludo',
  size = 120,
  className,
}: {
  pose?: MascotPose;
  size?: number;
  className?: string;
}) {
  const { src, ratio } = poses[pose];
  return (
    <img
      className={['mascot', className].filter(Boolean).join(' ')}
      src={src}
      alt=""
      height={size}
      width={Math.round(size * ratio)}
    />
  );
}
