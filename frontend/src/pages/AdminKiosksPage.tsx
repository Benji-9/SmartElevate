import { useId, useState } from 'react';
import { Button } from '../components/Button';
import { Chip } from '../components/Chip';
import { ConfirmAction } from '../components/ConfirmAction';
import { formatDateTime } from '../features/admin/format';
import { ElevatorSelect } from '../features/kiosk/ElevatorSelect';
import { errorMessage, useResource } from '../hooks/useResource';
import { ApiError, createKiosk, getKiosks, revokeKiosk } from '../services/api';
import type { CreatedKiosk } from '../types/api';
import '../features/admin/admin.css';
import '../features/kiosk/kiosk.css';

/** Credencial recién creada: se muestra una sola vez y sale de la pantalla al confirmar. */
function NewKioskSecret({ kiosk, onDone }: { kiosk: CreatedKiosk; onDone: () => void }) {
  const [copy, setCopy] = useState<string>();
  const copySecret = () =>
    // `clipboard` no existe fuera de HTTPS: cae al mismo mensaje de error.
    Promise.resolve()
      .then(() => navigator.clipboard.writeText(kiosk.secret))
      .then(
        () => setCopy('Copiada'),
        () => setCopy('No pudimos copiar. Seleccioná el texto y copialo a mano.'),
      );
  return (
    <div className="kiosk-secret">
      <p className="text-body-strong">Kiosco del ascensor {kiosk.elevatorId} dado de alta.</p>
      <p>
        Copiá la credencial ahora: no la vamos a volver a mostrar. Abrí /qr/{kiosk.elevatorId} en la
        tablet y pegala ahí.
      </p>
      <code aria-label="Credencial del kiosco">{kiosk.secret}</code>
      <div className="kiosk-secret__actions">
        <Button variant="secondary" block={false} onClick={copySecret}>
          Copiar credencial
        </Button>
        <Button block={false} onClick={onDone}>
          Listo, ya la guardé
        </Button>
        <p role="status">{copy}</p>
      </div>
    </div>
  );
}

/** 09c · Kioscos del QR: alta (con la credencial a la vista una sola vez), lista y revocación. */
export function AdminKiosksPage() {
  const createId = useId();
  const listId = useId();
  const list = useResource(getKiosks);
  const [elevator, setElevator] = useState('');
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<CreatedKiosk | null>(null);
  const [createError, setCreateError] = useState<string>();
  const [actionError, setActionError] = useState<string>();

  const create = () => {
    setCreating(true);
    setCreated(null);
    setCreateError(undefined);
    createKiosk({ elevatorId: elevator })
      .then(
        (kiosk) => {
          setCreated(kiosk);
          list.reload();
        },
        (error: unknown) => setCreateError(errorMessage(error)),
      )
      .finally(() => setCreating(false));
  };

  const revoke = (id: string) => {
    setActionError(undefined);
    revokeKiosk(id).then(list.reload, (error: unknown) => {
      // 404: la fila quedó vieja. Se vuelve a pedir la lista.
      if (error instanceof ApiError && error.status === 404) {
        setActionError(`${error.message}. Actualizamos la lista.`);
        list.reload();
      } else {
        setActionError(errorMessage(error));
      }
    });
  };

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
  } else if (list.data.kiosks.length === 0) {
    content = <p className="page-placeholder">Todavía no hay kioscos.</p>;
  } else {
    content = (
      <div className="core-table__scroll">
        <table className="core-table" aria-labelledby={listId}>
          <thead>
            <tr>
              <th scope="col">Ascensor</th>
              <th scope="col">Tipo</th>
              <th scope="col">Alta</th>
              <th scope="col">Última conexión</th>
              <th scope="col">Estado</th>
              <th scope="col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {list.data.kiosks.map((k) => (
              <tr key={k.id}>
                <th scope="row">{k.elevatorId}</th>
                <td>{k.simulated ? 'Simulador' : 'Tablet'}</td>
                <td>{formatDateTime(k.createdAt)}</td>
                <td>{k.lastSeenAt ? formatDateTime(k.lastSeenAt) : 'Nunca'}</td>
                <td>{k.revokedAt ? <Chip tone="neutral">Revocado</Chip> : <Chip>Activo</Chip>}</td>
                <td>
                  {!k.revokedAt && (
                    <ConfirmAction
                      label="Revocar"
                      name={`Revocar el kiosco del ascensor ${k.elevatorId}`}
                      question={`¿Revocar el kiosco del ascensor ${k.elevatorId}?`}
                      confirmLabel="Sí, revocar"
                      onConfirm={() => revoke(k.id)}
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
        <h1 className="text-title-lg">Kioscos del QR</h1>
        <p className="admin__context">
          Cada tablet del hall muestra el QR de un ascensor con su propia credencial. Si se pierde
          una, revocala y dá de alta otra.
        </p>
      </header>
      <section className="admin-card kiosk-card" aria-labelledby={createId}>
        <h2 id={createId} className="text-card-title">
          Dar de alta un kiosco
        </h2>
        <div className="kiosk-create">
          <ElevatorSelect value={elevator} onChange={setElevator} />
          <Button
            block={false}
            disabled={!elevator}
            loading={creating}
            loadingLabel="Dando de alta…"
            onClick={create}
          >
            Dar de alta
          </Button>
        </div>
        {createError && (
          <p role="alert" className="kiosk-error">
            {createError}
          </p>
        )}
        {created && <NewKioskSecret kiosk={created} onDone={() => setCreated(null)} />}
      </section>
      <section className="admin-card kiosk-card" aria-labelledby={listId}>
        <h2 id={listId} className="text-card-title">
          Kioscos
        </h2>
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
