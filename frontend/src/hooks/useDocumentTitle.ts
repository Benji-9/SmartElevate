import { useEffect } from 'react';

/** Título de la pestaña por pantalla ("Reservar turno · SmartElevate"); lo leen los lectores al navegar. */
export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = `${title} · SmartElevate`;
  }, [title]);
}
