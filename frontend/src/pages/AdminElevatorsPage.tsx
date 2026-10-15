import { useId, useState } from 'react';
import { Button } from '../components/Button';
import { Chip } from '../components/Chip';
import { ConfirmAction } from '../components/ConfirmAction';
import { errorMessage, useResource } from '../hooks/useResource';
import {
  ApiError,
  getAdminElevators,
  markElevatorInService,
  markElevatorOutOfService,
} from '../services/api';
import type { AdminElevator } from '../types/api';
import '../features/admin/admin.css';
import '../features/kiosk/kiosk.css';

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

/** Qué pasa al marcarlo: el conteo sale de la última lista cargada. */
function outageQuestion({ code, upcomingReservations: n }: AdminElevator) {
  const effect =
    n === 0
      ? 'No hay reservas para cancelar.'
      : n === 1
        ? 'Se cancela 1 reserva sin falta y se avisa a la persona.'
        : `Se cancelan ${n} reservas sin falta y se avisa a cada persona.`;
  return `¿Marcar el ascensor ${code} fuera de servicio? ${effect}`;
}

/** 09e · Ascensores: marcar fuera de servicio por avería (cancela las reservas sin falta) o reactivar. */
export function AdminElevatorsPage() {
  const listId = useId();
  const list = useResource(getAdminElevators);
  const [done, setDone] = useState<string>();
  const [actionError, setActionError] = useState<string>();

  const change = (promise: Promise<unknown>, onOk: () => string | undefined) => {
    setDone(undefined);
    setActionError(undefined);
    promise.then(
      () => {
        setDone(onOk());
        list.reload();
      },
      (error: unknown) => {
        // 404: la fila quedó vieja. Se vuelve a pedir la lista.
        if (error instanceof ApiError && error.status === 404) {
          setActionError(`${error.message}. Actualizamos la lista.`);
          list.reload();
        } else {
          setActionError(errorMessage(error));
        }
      },
    );
  };

  const markOut = (code: string) => {
    let cancelled = 0;
    change(
      markElevatorOutOfService(code).then((r) => {
        cancelled = r.cancelledReservations;
      }),
      () =>
        `Ascensor ${code} fuera de servicio. Se cancelaron ${plural(cancelled, 'reserva', 'reservas')}.`,
    );
  };

  const markIn = (code: string) =>
    change(markElevatorInService(code), () => `Ascensor ${code} en servicio de nuevo.`);

  let content;
  if (list.error) {
    content = (
      <div role="alert" className="admin__error">
        <p>{list.error}</p>
        <Button variant="secondary" block={false} onClick={list.reload}>
          Reintentar
        </Button>
      </div>
    );
  } else if (!list.data) {
    content = (
      <p role="status" className="page-placeholder">
        Cargando…
      </p>
    );
  } else if (list.data.length === 0) {
    content = <p className="page-placeholder">No hay ascensores para mostrar.</p>;
  } else {
    content = (
      <div className="core-table__scroll">
        <table className="core-table" aria-labelledby={listId}>
          <thead>
            <tr>
              <th scope="col">Ascensor</th>
              <th scope="col">Núcleo</th>
              <th scope="col">Reservas de hoy</th>
              <th scope="col">Estado</th>
              <th scope="col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {list.data.map((e) => (
              <tr key={e.code}>
                <th scope="row">{e.code}</th>
                <td>{e.coreName}</td>
                <td>{e.upcomingReservations}</td>
                <td>
                  {e.active ? (
                    <Chip>En servicio</Chip>
                  ) : (
                    <Chip tone="neutral">Fuera de servicio</Chip>
                  )}
                </td>
                <td>
                  {e.active ? (
                    <ConfirmAction
                      label="Marcar fuera de servicio"
                      name={`Marcar fuera de servicio el ascensor ${e.code}`}
                      question={outageQuestion(e)}
                      confirmLabel="Sí, marcar"
                      onConfirm={() => markOut(e.code)}
                    />
                  ) : (
                    <ConfirmAction
                      label="Reactivar"
                      name={`Reactivar el ascensor ${e.code}`}
                      question={`¿Reactivar el ascensor ${e.code}? Vuelve a ofrecerse para reservar. Las reservas canceladas no se restauran.`}
                      confirmLabel="Sí, reactivar"
                      onConfirm={() => markIn(e.code)}
                    />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <>
      <header>
        <h1 className="text-title-lg">Ascensores</h1>
        <p className="admin__context">
          Si un ascensor se rompe, marcalo fuera de servicio: sus reservas de hoy se cancelan solas,
          sin falta, y avisamos a cada persona por mail.
        </p>
      </header>
      <section className="admin-card kiosk-card" aria-labelledby={listId}>
        <h2 id={listId} className="text-card-title">
          Ascensores
        </h2>
        {done && <p role="status">{done}</p>}
        {actionError && (
          <p role="alert" className="kiosk-error">
            {actionError}
          </p>
        )}
        {content}
      </section>
    </>
  );
}
