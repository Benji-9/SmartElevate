import { useEffect, useId, useRef, useState } from 'react';
import { Button } from '../components/Button';
import { Chip } from '../components/Chip';
import { ConfirmAction } from '../components/ConfirmAction';
import { errorMessage, useResource } from '../hooks/useResource';
import {
  ApiError,
  getAdminElevators,
  getBuildings,
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
  // Solo para el nombre visible del edificio: si falla, se muestra el código.
  const buildings = useResource(getBuildings);
  const [done, setDone] = useState<string>();
  const [actionError, setActionError] = useState<string>();
  // Un solo pedido a la vez (evita doble POST) y nada de setState si la pantalla ya se desmontó.
  const [busy, setBusy] = useState(false);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const change = (promise: Promise<unknown>, onOk: () => string | undefined) => {
    setDone(undefined);
    setActionError(undefined);
    setBusy(true);
    promise.then(
      () => {
        if (!mounted.current) return;
        setBusy(false);
        setDone(onOk());
        list.reload();
      },
      (error: unknown) => {
        if (!mounted.current) return;
        setBusy(false);
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
    const names = new Map((buildings.data ?? []).map((b) => [b.code, b.name]));
    // Edificios y, dentro de cada uno, núcleos en el orden en que aparecen.
    const byBuilding = new Map<string, Map<string, AdminElevator[]>>();
    for (const e of list.data) {
      const cores = byBuilding.get(e.building) ?? new Map<string, AdminElevator[]>();
      cores.set(e.coreName, [...(cores.get(e.coreName) ?? []), e]);
      byBuilding.set(e.building, cores);
    }
    content = (
      <div className="elevator-buildings">
        {[...byBuilding].map(([building, cores]) => (
          <section key={building} aria-label={names.get(building) ?? building}>
            <h3 className="text-label elevator-buildings__name">
              {names.get(building) ?? building}
            </h3>
            {[...cores].map(([core, items]) => (
              <section key={core} aria-label={core} className="elevator-core">
                <h4 className="text-body-strong">{core}</h4>
                <ul className="elevator-grid">
                  {items.map((e) => (
                    <li key={e.code}>
                      <article
                        className={`elevator-card${e.active ? '' : ' elevator-card--out'}`}
                        aria-label={`Ascensor ${e.code}`}
                      >
                        <header className="elevator-card__head">
                          <h5 className="text-card-title">{e.code}</h5>
                          {e.active ? (
                            <Chip>En servicio</Chip>
                          ) : (
                            <Chip tone="danger">Fuera de servicio</Chip>
                          )}
                        </header>
                        {e.usage !== 'COMMON' && (
                          <p>
                            <Chip tone={e.usage === 'TEACHERS' ? 'docentes' : 'neutral'}>
                              {e.usage === 'TEACHERS'
                                ? 'Dedicado a docentes'
                                : 'Dedicado a movilidad reducida'}
                            </Chip>
                          </p>
                        )}
                        <p className="elevator-card__meta">
                          {plural(e.upcomingReservations, 'reserva próxima', 'reservas próximas')}
                        </p>
                        {e.active ? (
                          <ConfirmAction
                            label="Marcar fuera de servicio"
                            name={`Marcar fuera de servicio el ascensor ${e.code}`}
                            question={outageQuestion(e)}
                            confirmLabel="Sí, marcar"
                            disabled={busy}
                            onConfirm={() => markOut(e.code)}
                          />
                        ) : (
                          <ConfirmAction
                            label="Reactivar"
                            name={`Reactivar el ascensor ${e.code}`}
                            question={`¿Reactivar el ascensor ${e.code}? Vuelve a ofrecerse para reservar. Las reservas canceladas no se restauran.`}
                            confirmLabel="Sí, reactivar"
                            disabled={busy}
                            onConfirm={() => markIn(e.code)}
                          />
                        )}
                      </article>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </section>
        ))}
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
          Estado de los ascensores
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
