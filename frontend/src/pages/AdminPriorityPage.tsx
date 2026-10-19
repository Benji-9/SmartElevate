import { useState } from 'react';
import { PendingTeachers, PriorityUsers } from '../features/admin/PrioritySections';
import { RoleSearch } from '../features/admin/RoleSearch';
import '../features/admin/admin.css';

/** Figma-less 09b · Gestión de usuarios: cola de docentes y prioridades vigentes (#140). */
export function AdminPriorityPage() {
  // Aprobar a un docente suma una fila a la otra lista: cambiar la key la vuelve a pedir.
  const [version, setVersion] = useState(0);
  return (
    <>
      <header>
        <h1 className="text-title-lg">Gestión de usuarios</h1>
        <p className="admin__context">
          Validá a los docentes, gestioná quién tiene prioridad y buscá usuarios para cambiar su
          rol.
        </p>
      </header>
      <PendingTeachers onApproved={() => setVersion((v) => v + 1)} />
      <PriorityUsers key={version} />
      <RoleSearch />
    </>
  );
}
