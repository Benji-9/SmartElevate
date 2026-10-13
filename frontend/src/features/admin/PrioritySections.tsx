import { useId, useState, type ReactNode } from 'react';
import { Button } from '../../components/Button';
import { Chip } from '../../components/Chip';
import { errorMessage, useResource } from '../../hooks/useResource';
import {
  approveTeacher,
  getPendingTeachers,
  getPriorityUsers,
  rejectTeacher,
  revokePriority,
} from '../../services/api';
import { formatRegisteredAt } from './format';
import './priority.css';

/** Botón que pide confirmación en la misma fila antes de ejecutar (rechazar / revocar). */
function ConfirmAction({
  label,
  name,
  question,
  confirmLabel,
  onConfirm,
}: {
  label: string;
  /** Nombre accesible completo ("Rechazar a Ana"). */
  name: string;
  question: string;
  confirmLabel: string;
  onConfirm: () => void;
}) {
  const [asking, setAsking] = useState(false);
  if (!asking) {
    return (
      <Button variant="secondary" block={false} aria-label={name} onClick={() => setAsking(true)}>
        {label}
      </Button>
    );
  }
  return (
    <div role="group" aria-label={question} className="priority-confirm">
      <p>{question}</p>
      <Button
        block={false}
        onClick={() => {
          setAsking(false);
          onConfirm();
        }}
      >
        {confirmLabel}
      </Button>
      {/* El foco empieza en la opción segura. */}
      <Button variant="secondary" block={false} autoFocus onClick={() => setAsking(false)}>
        Cancelar
      </Button>
    </div>
  );
}

/** Tarjeta con título, estados de carga / error / vacío y una tabla de personas. */
function Section<T extends { id: string }>({
  title,
  load,
  empty,
  head,
  row,
  onDone,
}: {
  /** Se llama cuando una acción terminó bien (p. ej. para refrescar la otra lista). */
  onDone?: () => void;
  title: string;
  load: () => Promise<T[]>;
  empty: string;
  head: string;
  row: (item: T, run: (action: () => Promise<unknown>) => void) => ReactNode;
}) {
  const titleId = useId();
  const list = useResource(load);
  const [actionError, setActionError] = useState<string>();

  // Tras la acción se vuelve a pedir la lista: la fila sale sin recargar la página.
  const run = (action: () => Promise<unknown>) => {
    setActionError(undefined);
    action().then(
      () => {
        list.reload();
        onDone?.();
      },
      (error: unknown) => setActionError(errorMessage(error)),
    );
  };

  let content: ReactNode;
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
    content = <p className="page-placeholder">{empty}</p>;
  } else {
    content = (
      <div className="core-table__scroll">
        <table className="core-table priority-table" aria-labelledby={titleId}>
          <thead>
            <tr>
              <th scope="col">Nombre</th>
              <th scope="col">Email</th>
              <th scope="col">Legajo</th>
              <th scope="col">{head}</th>
              <th scope="col">Acciones</th>
            </tr>
          </thead>
          <tbody>{list.data.map((item) => row(item, run))}</tbody>
        </table>
      </div>
    );
  }

  return (
    <section className="admin-card" aria-labelledby={titleId}>
      <h2 id={titleId} className="text-card-title">
        {title}
      </h2>
      {actionError && (
        <p role="alert" className="priority-error">
          {actionError}
        </p>
      )}
      {content}
    </section>
  );
}

export function PendingTeachers({ onApproved }: { onApproved: () => void }) {
  return (
    <Section
      onDone={onApproved}
      title="Docentes a validar"
      load={getPendingTeachers}
      empty="No hay docentes para validar."
      head="Registro"
      row={(t, run) => (
        <tr key={t.id}>
          <th scope="row">{t.fullName}</th>
          <td>{t.email}</td>
          <td>{t.legajo}</td>
          <td>{formatRegisteredAt(t.registeredAt)}</td>
          <td>
            <div className="priority-actions">
              <Button
                block={false}
                aria-label={`Aprobar a ${t.fullName}`}
                onClick={() => run(() => approveTeacher(t.id))}
              >
                Aprobar
              </Button>
              <ConfirmAction
                label="Rechazar"
                name={`Rechazar a ${t.fullName}`}
                question={`¿Rechazar a ${t.fullName}?`}
                confirmLabel="Sí, rechazar"
                onConfirm={() => run(() => rejectTeacher(t.id))}
              />
            </div>
          </td>
        </tr>
      )}
    />
  );
}

export function PriorityUsers() {
  return (
    <Section
      title="Usuarios con prioridad"
      load={getPriorityUsers}
      empty="Todavía no hay usuarios con prioridad."
      head="Categoría"
      row={(u, run) => (
        <tr key={u.id}>
          <th scope="row">{u.fullName}</th>
          <td>{u.email}</td>
          <td>{u.legajo}</td>
          <td>
            {u.category === 'TEACHER' ? (
              <Chip tone="docentes">Docente</Chip>
            ) : (
              <Chip>Movilidad reducida</Chip>
            )}
          </td>
          <td>
            <ConfirmAction
              label="Revocar"
              name={`Revocar a ${u.fullName}`}
              question={`¿Revocar la prioridad de ${u.fullName}?`}
              confirmLabel="Sí, revocar"
              onConfirm={() => run(() => revokePriority(u.id))}
            />
          </td>
        </tr>
      )}
    />
  );
}
