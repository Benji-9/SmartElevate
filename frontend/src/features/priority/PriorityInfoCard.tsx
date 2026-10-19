import './priority.css';

/** Tarjeta informativa: la marca de movilidad reducida la gestiona UADE (OFALU), no la app. */
export function PriorityInfoCard() {
  return (
    <section className="priority-card" aria-labelledby="priority-title">
      <h2 id="priority-title">Acceso prioritario</h2>
      <p className="priority-card__text">
        Si tenés movilidad reducida, temporal o permanente, informalo en la Oficina de Alumnos
        (OFALU) de UADE. SmartElevate no te pide certificados ni datos de salud.
      </p>
    </section>
  );
}
