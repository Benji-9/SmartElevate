import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { Button } from '../../components/Button';
import { Chip } from '../../components/Chip';
import { Input } from '../../components/Input';
import { errorMessage, useResource } from '../../hooks/useResource';
import { useSession } from '../../hooks/useSession';
import { ApiError, assignRole, changeUserType, searchUsers } from '../../services/api';
import type { AdminUser, Role } from '../../types/api';
import './priority.css';

/** El servidor exige el mismo mínimo (400 si es menor). */
const MIN_QUERY = 3;
const DEBOUNCE_MS = 300;
const ROLES: Record<Role, string> = {
  USER: 'Usuario',
  REVIEWER: 'Revisor',
  ADMIN: 'Administrador',
};

const USER_TYPES: Record<AdminUser['userType'], string> = {
  STUDENT: 'Estudiante',
  TEACHER: 'Docente',
};

type UserType = AdminUser['userType'];
/** Lo que se está por cambiar en una fila: el rol o el tipo. */
type Edit = { field: 'role'; to: Role } | { field: 'type'; to: UserType };

/**
 * Fila con el engranaje de gestión. Cambiar el rol o el tipo pide confirmación en el lugar.
 * Bloquear la cuenta todavía no tiene endpoint: se muestra sin acción.
 */
function UserRow({
  user,
  isSelf,
  busy,
  onChange,
}: {
  user: AdminUser;
  isSelf: boolean;
  /** Hay un cambio en vuelo para esta fila. */
  busy: boolean;
  onChange: (edit: Edit) => void;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [editing, setEditing] = useState<Edit['field']>();
  const [picked, setPicked] = useState<Edit>();
  const menuId = useId();
  const close = () => {
    setMenuOpen(false);
    setEditing(undefined);
    setPicked(undefined);
  };
  const labels: Record<string, string> = picked?.field === 'type' ? USER_TYPES : ROLES;
  const current = picked?.field === 'type' ? user.userType : user.role;
  let action;
  if (isSelf) {
    action = 'Es tu cuenta';
  } else if (picked) {
    action = (
      <div
        role="group"
        aria-label={`Cambiar ${picked.field === 'type' ? 'tipo' : 'rol'} de ${user.fullName}`}
        className="priority-actions"
      >
        <p className="priority-question">
          ¿Cambiar a {user.fullName} de {labels[current]} a {labels[picked.to]}?
          {picked.field === 'type' &&
            picked.to === 'TEACHER' &&
            ' Va a tener prioridad de docente hasta fin del cuatrimestre.'}
          {picked.field === 'type' && picked.to === 'STUDENT' && ' Pierde la prioridad de docente.'}
        </p>
        <Button
          block={false}
          onClick={() => {
            onChange(picked);
            close();
          }}
        >
          Sí, cambiar
        </Button>
        <Button variant="secondary" block={false} autoFocus onClick={() => setPicked(undefined)}>
          Cancelar
        </Button>
      </div>
    );
  } else if (editing) {
    const options = editing === 'type' ? USER_TYPES : ROLES;
    action = (
      <div className="priority-actions">
        <select
          className="priority-select"
          aria-label={`${editing === 'type' ? 'Tipo' : 'Rol'} de ${user.fullName}`}
          disabled={busy}
          value={editing === 'type' ? user.userType : user.role}
          autoFocus
          onChange={(e) =>
            setPicked(
              editing === 'type'
                ? { field: 'type', to: e.target.value as UserType }
                : { field: 'role', to: e.target.value as Role },
            )
          }
        >
          {Object.entries(options).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <Button variant="secondary" block={false} className="button--compact" onClick={close}>
          Cerrar
        </Button>
      </div>
    );
  } else {
    action = (
      <div
        className="user-menu"
        onKeyDown={(e) => {
          if (e.key === 'Escape') setMenuOpen(false);
        }}
      >
        <Button
          variant="secondary"
          block={false}
          className="button--compact user-menu__gear"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          aria-controls={menuId}
          aria-label={`Gestionar usuario ${user.fullName}`}
          disabled={busy}
          onClick={() => setMenuOpen((open) => !open)}
        >
          <svg
            aria-hidden="true"
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3h0a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5h0a1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8v0a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" />
          </svg>
        </Button>
        {menuOpen && (
          <ul
            id={menuId}
            role="menu"
            aria-label={`Gestionar a ${user.fullName}`}
            className="user-menu__list"
          >
            <li role="none">
              <button
                type="button"
                role="menuitem"
                className="user-menu__item"
                autoFocus
                onClick={() => {
                  setMenuOpen(false);
                  setEditing('role');
                }}
              >
                Modificar rol
              </button>
            </li>
            <li role="none">
              <button
                type="button"
                role="menuitem"
                className="user-menu__item"
                onClick={() => {
                  setMenuOpen(false);
                  setEditing('type');
                }}
              >
                Cambiar tipo
              </button>
            </li>
            <li role="none">
              <button
                type="button"
                role="menuitem"
                className="user-menu__item"
                aria-disabled="true"
              >
                Bloquear cuenta
                <span className="user-menu__soon">Próximamente</span>
              </button>
            </li>
          </ul>
        )}
      </div>
    );
  }
  return (
    <tr>
      <th scope="row">{user.fullName}</th>
      <td>{user.email}</td>
      <td>{user.legajo}</td>
      <td>
        <Chip tone="neutral">{USER_TYPES[user.userType]}</Chip>
      </td>
      <td>
        <Chip tone="neutral">{ROLES[user.role]}</Chip>
      </td>
      <td>{action}</td>
    </tr>
  );
}

/** Resultados de una búsqueda. Se remonta (key) al cambiar el texto o al pedir "Reintentar". */
function Results({
  term,
  titleId,
  onStale,
}: {
  term: string;
  titleId: string;
  onStale: () => void;
}) {
  const { user: me } = useSession();
  const first = useResource(useCallback(() => searchUsers(term), [term]));
  const [more, setMore] = useState<{ items: AdminUser[]; next: string | null }>();
  // Lo ya confirmado en esta búsqueda, para mostrarlo sin volver a pedir la lista.
  const [roles, setRoles] = useState<Record<string, Role>>({});
  const [types, setTypes] = useState<Record<string, UserType>>({});
  const [loadingMore, setLoadingMore] = useState(false);
  const [saving, setSaving] = useState<string[]>([]);
  // La búsqueda cambió (o se desmontó): las respuestas de pedidos viejos se ignoran.
  const ignore = useRef(false);
  useEffect(() => {
    ignore.current = false;
    return () => {
      ignore.current = true;
    };
  }, []);
  const [actionError, setActionError] = useState<string>();

  if (first.error) {
    return (
      <div role="alert" className="admin__error">
        <p>{first.error}</p>
        <Button variant="secondary" block={false} onClick={onStale}>
          Reintentar
        </Button>
      </div>
    );
  }
  if (!first.data) {
    return (
      <p role="status" className="page-placeholder">
        Buscando…
      </p>
    );
  }
  const items = [...first.data.items, ...(more?.items ?? [])];
  const next = more ? more.next : first.data.nextCursor;
  if (items.length === 0) {
    return <p className="page-placeholder">No encontramos usuarios para «{term}».</p>;
  }

  const change = (user: AdminUser, edit: Edit) => {
    setActionError(undefined);
    setSaving((ids) => [...ids, user.id]);
    const done = () => setSaving((ids) => ids.filter((id) => id !== user.id));
    const request =
      edit.field === 'role' ? assignRole(user.id, edit.to) : changeUserType(user.id, edit.to);
    request.then(
      () => {
        done();
        if (edit.field === 'role') setRoles((r) => ({ ...r, [user.id]: edit.to }));
        else setTypes((t) => ({ ...t, [user.id]: edit.to }));
      },
      (error: unknown) => {
        done();
        if (error instanceof ApiError && error.status === 404) {
          setActionError(`${error.message}. Actualizamos la lista.`);
          onStale();
        } else {
          setActionError(errorMessage(error));
        }
      },
    );
  };
  const loadMore = () => {
    if (!next) return;
    setLoadingMore(true);
    searchUsers(term, next).then(
      (page) => {
        if (ignore.current) return;
        setMore({ items: [...(more?.items ?? []), ...page.items], next: page.nextCursor ?? null });
        setLoadingMore(false);
      },
      (error: unknown) => {
        if (ignore.current) return;
        setActionError(errorMessage(error));
        setLoadingMore(false);
      },
    );
  };

  return (
    <>
      {actionError && (
        <p role="alert" className="priority-error">
          {actionError}
        </p>
      )}
      <div className="core-table__scroll">
        <table className="core-table priority-table" aria-labelledby={titleId}>
          <thead>
            <tr>
              <th scope="col">Nombre</th>
              <th scope="col">Email</th>
              <th scope="col">Legajo</th>
              <th scope="col">Tipo</th>
              <th scope="col">Rol</th>
              <th scope="col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {items.map((u) => (
              <UserRow
                key={u.id}
                user={{ ...u, role: roles[u.id] ?? u.role, userType: types[u.id] ?? u.userType }}
                isSelf={u.id === me?.id}
                busy={saving.includes(u.id)}
                onChange={(edit) => change(u, edit)}
              />
            ))}
          </tbody>
        </table>
      </div>
      {next && (
        <Button variant="secondary" block={false} disabled={loadingMore} onClick={loadMore}>
          Ver más resultados
        </Button>
      )}
    </>
  );
}

/** Tarjeta "Buscar y gestionar" (SCREENS.md 09b): buscador con debounce y gestión por usuario. */
export function RoleSearch() {
  const titleId = useId();
  const [query, setQuery] = useState('');
  const [term, setTerm] = useState('');
  const [version, setVersion] = useState(0);

  useEffect(() => {
    const timer = setTimeout(() => setTerm(query.trim()), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [query]);

  return (
    <section className="admin-card role-search" aria-labelledby={titleId}>
      <h2 id={titleId} className="text-card-title">
        Buscar usuarios
      </h2>
      <Input
        label="Buscar usuario"
        type="search"
        hint={`Por nombre, email o legajo (mínimo ${MIN_QUERY} caracteres)`}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      {term.length >= MIN_QUERY && (
        <Results
          key={`${term}:${version}`}
          term={term}
          titleId={titleId}
          onStale={() => setVersion((v) => v + 1)}
        />
      )}
    </section>
  );
}
