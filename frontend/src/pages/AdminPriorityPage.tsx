import { useState } from 'react';
import { PendingTeachers, PriorityUsers } from '../features/admin/PrioritySections';
import { RoleSearch } from '../features/admin/RoleSearch';
import '../features/admin/admin.css';

/** Figma-less 09b · Gestión de usuarios: cola de docentes y prioridades vigentes (#140). */
export function AdminPriorityPage() {
  // Aprobar a un docente o cambiar un tipo mueve filas entre las listas: cambiar la key las vuelve a pedir.
  const [version, setVersion] = useState(0);
  const refresh = () => setVersion((v) => v + 1);
  return (
    <>
      <header>
        <h1 className="text-title-lg">Gestión de usuarios</h1>
        <p className="admin__context">
          Validá a los docentes, gestioná quién tiene prioridad y buscá usuarios para cambiar su
          rol.
        </p>
      </header>
      <PendingTeachers key={`p${version}`} onApproved={refresh} />
      <PriorityUsers key={`u${version}`} />
      <RoleSearch onTypeChanged={refresh} />
    </>
  );
}
