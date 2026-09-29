import { useParams } from 'react-router';

export function TurnPage() {
  const { id } = useParams();
  return (
    <section>
      <h1>Tu turno</h1>
      <p className="page-placeholder">Próximamente: detalle del turno {id} y cancelación.</p>
    </section>
  );
}
