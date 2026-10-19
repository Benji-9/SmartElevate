import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { Button } from '../../components/Button';
import { Chip } from '../../components/Chip';
import { Input } from '../../components/Input';
import { errorMessage, useResource } from '../../hooks/useResource';
import { useSession } from '../../hooks/useSession';
import { ApiError, assignRole, changeDeclaredType, searchUsers } from '../../services/api';
import type { AdminUser, DeclaredUserType, Role } from '../../types/api';
import './priority.css';

/** El servidor exige el mismo mínimo (400 si es menor). */
const MIN_QUERY = 3;
const DEBOUNCE_MS = 300;
const ROLES: Record<Role, string> = {
  USER: 'Usuario',
  REVIEWER: 'Revisor',
  ADMIN: 'Administrador',
};

const TYPES: Record<DeclaredUserType, string> = {
  STUDENT: 'Estudiante',
  TEACHER: 'Docente',
};

/**
 * Tipo de usuario (estudiante o docente) y su cambio, con confirmación en el lugar. Pasar a docente lo deja
 * en la cola de validación; pasar a estudiante le quita la prioridad de docente. Exige el email verificado.
 */
function TypeCell({
  user,
  busy,
  onChange,
}: {
  user: AdminUser;
  busy: boolean;
  onChange: (type: DeclaredUserType) => void;
}) {
  const [asking, setAsking] = useState(false);
  const target: DeclaredUserType = user.declaredUserType === 'TEACHER' ? 'STUDENT' : 'TEACHER';
  let action;
  if (asking) {
    action = (
      <div
        role="group"
        aria-label={`Cambiar tipo de ${user.fullName}`}
        className="priority-actions"
      >
        <p className="priority-question">
          ¿Pasar a {user.fullName} de {TYPES[user.declaredUserType]} a {TYPES[target]}?
          {target === 'STUDENT' && ' Si tiene la prioridad de docente vigente, se la quitamos.'}
          {target === 'TEACHER' && ' Queda en «Docentes a validar» hasta que lo apruebes.'}
        </p>
        <Button
          block={false}
          onClick={() => {
            onChange(target);
            setAsking(false);
          }}
        >
          Sí, pasar
        </Button>
        <Button variant="secondary" block={false} autoFocus onClick={() => setAsking(false)}>
          Cancelar
        </Button>
      </div>
    );
  } else if (!user.emailVerified) {
    action = <span className="text-caption">Email sin verificar</span>;
  } else {
    action = (
      <Button
        variant="secondary"
        block={false}
        disabled={busy}
        aria-label={`Pasar a ${user.fullName} a ${TYPES[target].toLowerCase()}`}
        onClick={() => setAsking(true)}
      >
        Pasar a {TYPES[target].toLowerCase()}
      </Button>
    );
  }
  return (
    <div className="priority-type">
      <Chip tone={user.declaredUserType === 'TEACHER' ? 'docentes' : 'alumnos'}>
        {TYPES[user.declaredUserType]}
      </Chip>
      {action}
    </div>
  );
}

/** Fila con selector de rol; elegir otro rol pide confirmación en el lugar. */
function RoleRow({
  user,
  isSelf,
  busy,
  onChange,
  onChangeType,
}: {
  user: AdminUser;
  isSelf: boolean;
  /** Hay un cambio (de rol o de tipo) en vuelo para esta fila. */
  busy: boolean;
  onChange: (role: Role) => void;
  onChangeType: (type: DeclaredUserType) => void;
}) {
  const [picked, setPicked] = useState<Role>();
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
            setPicked(undefined);
          }}
        >
          Sí, cambiar
        </Button>
        <Button variant="secondary" block={false} autoFocus onClick={() => setPicked(undefined)}>
          Cancelar
        </Button>
      </div>
    );
  } else {
    action = (
      <select
        className="priority-select"
        aria-label={`Rol de ${user.fullName}`}
        disabled={busy}
        value={user.role}
        onChange={(e) => setPicked(e.target.value as Role)}
      >
        {Object.entries(ROLES).map(([role, label]) => (
          <option key={role} value={role}>
            {label}
          </option>
        ))}
      </select>
    );
  }
  return (
    <tr>
      <th scope="row">{user.fullName}</th>
      <td>{user.email}</td>
      <td>{user.legajo}</td>
      <td>
        <Chip tone={user.role === 'USER' ? 'neutral' : 'accent'}>{ROLES[user.role]}</Chip>
      </td>
      <td>
        <TypeCell user={user} busy={busy} onChange={onChangeType} />
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
  const [types, setTypes] = useState<Record<string, DeclaredUserType>>({});
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

  // Un cambio de la fila: lo manda y, si sale bien, lo refleja sin recargar. 404 = la fila quedó vieja.
  const save = (user: AdminUser, request: Promise<void>, applied: () => void) => {
    setActionError(undefined);
    setSaving((ids) => [...ids, user.id]);
    const done = () => setSaving((ids) => ids.filter((id) => id !== user.id));
    request.then(
      () => {
        done();
        applied();
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
  const change = (user: AdminUser, role: Role) =>
    save(user, assignRole(user.id, role), () => setRoles((r) => ({ ...r, [user.id]: role })));
  const changeType = (user: AdminUser, type: DeclaredUserType) =>
    save(user, changeDeclaredType(user.id, type), () =>
      setTypes((t) => ({ ...t, [user.id]: type })),
    );
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
              <RoleRow
                key={u.id}
                user={{
                  ...u,
                  role: roles[u.id] ?? u.role,
                  declaredUserType: types[u.id] ?? u.declaredUserType,
                }}
                isSelf={u.id === me?.id}
                busy={saving.includes(u.id)}
                onChange={(role) => change(u, role)}
                onChangeType={(type) => changeType(u, type)}
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

/** Tarjeta "Roles de usuarios" (SCREENS.md 09b): buscador con debounce y cambio de rol y de tipo confirmados. */
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
        Roles de usuarios
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
