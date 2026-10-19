import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { Button } from '../../components/Button';
import { Chip } from '../../components/Chip';
import { Input } from '../../components/Input';
import { errorMessage, useResource } from '../../hooks/useResource';
import { useSession } from '../../hooks/useSession';
import { ApiError, assignRole, searchUsers } from '../../services/api';
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

/**
 * Fila con el menú "Gestionar usuario". Cambiar el rol pide confirmación en el lugar.
 * Cambiar el tipo y bloquear todavía no tienen endpoint: se muestran sin acción.
 */
function UserRow({
  user,
  isSelf,
  busy,
  onChange,
}: {
  user: AdminUser;
  isSelf: boolean;
  /** Hay un cambio de rol en vuelo para esta fila. */
  busy: boolean;
  onChange: (role: Role) => void;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [editingRole, setEditingRole] = useState(false);
  const [picked, setPicked] = useState<Role>();
  const menuId = useId();
  const close = () => {
    setMenuOpen(false);
    setEditingRole(false);
    setPicked(undefined);
  };
  let action;
  if (isSelf) {
    action = 'Es tu cuenta';
  } else if (picked) {
    action = (
      <div role="group" aria-label={`Cambiar rol de ${user.fullName}`} className="priority-actions">
        <p className="priority-question">
          ¿Cambiar a {user.fullName} de {ROLES[user.role]} a {ROLES[picked]}?
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
  } else if (editingRole) {
    action = (
      <div className="priority-actions">
        <select
          className="priority-select"
          aria-label={`Rol de ${user.fullName}`}
          disabled={busy}
          value={user.role}
          autoFocus
          onChange={(e) => setPicked(e.target.value as Role)}
        >
          {Object.entries(ROLES).map(([role, label]) => (
            <option key={role} value={role}>
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
          className="button--compact"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          aria-controls={menuId}
          aria-label={`Gestionar usuario ${user.fullName}`}
          disabled={busy}
          onClick={() => setMenuOpen((open) => !open)}
        >
          Gestionar usuario
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
                  setEditingRole(true);
                }}
              >
                Cambiar rol
              </button>
            </li>
            <li role="none">
              <button
                type="button"
                role="menuitem"
                className="user-menu__item"
                aria-disabled="true"
              >
                Cambiar tipo
                <span className="user-menu__soon">Próximamente</span>
              </button>
            </li>
            <li role="none">
              <button
                type="button"
                role="menuitem"
                className="user-menu__item"
                aria-disabled="true"
              >
                Bloquear usuario
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
        <Chip tone="neutral">{ROLES[user.role]}</Chip>
      </td>
      <td>
        <Chip tone="neutral">{USER_TYPES[user.userType]}</Chip>
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
  const [roles, setRoles] = useState<Record<string, Role>>({});
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

  const change = (user: AdminUser, role: Role) => {
    setActionError(undefined);
    setSaving((ids) => [...ids, user.id]);
    const done = () => setSaving((ids) => ids.filter((id) => id !== user.id));
    assignRole(user.id, role).then(
      () => {
        done();
        setRoles((r) => ({ ...r, [user.id]: role }));
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
              <th scope="col">Rol</th>
              <th scope="col">Tipo</th>
              <th scope="col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {items.map((u) => (
              <UserRow
                key={u.id}
                user={{ ...u, role: roles[u.id] ?? u.role }}
                isSelf={u.id === me?.id}
                busy={saving.includes(u.id)}
                onChange={(role) => change(u, role)}
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
