/** Destinos de la navegación principal: los comparten la BottomNav (móvil) y el TopBar (Pantalla). */
export const navItems = [
  {
    to: '/',
    label: 'Inicio',
    end: true,
    icon: <path d="M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1z" />,
  },
  {
    to: '/reservar',
    label: 'Reservar',
    end: false,
    icon: (
      <>
        <rect x="4" y="5" width="16" height="16" rx="2" />
        <path d="M8 3v4M16 3v4M4 10h16M12 13v5M9.5 15.5h5" />
      </>
    ),
  },
  {
    to: '/check-in',
    label: 'Check-in',
    end: false,
    icon: (
      <>
        <rect x="4" y="4" width="6" height="6" rx="1" />
        <rect x="14" y="4" width="6" height="6" rx="1" />
        <rect x="4" y="14" width="6" height="6" rx="1" />
        <path d="M14 14h2v2h-2zM18 18h2v2h-2zM14 18h2M18 14h2" />
      </>
    ),
  },
  {
    to: '/perfil',
    label: 'Perfil',
    end: false,
    icon: (
      <>
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21a8 8 0 0 1 16 0" />
      </>
    ),
  },
];
