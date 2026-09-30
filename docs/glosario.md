# Glosario

Términos del dominio, para que backend, frontend y docs hablen igual. Entre paréntesis, el nombre sugerido en código.

| Término | Significado |
| --- | --- |
| **Salida** / **franja** (`Departure`) | Viaje programado de un ascensor, cada ~2 min. Es la unidad que se reserva. Capacidad: 10. |
| **Reserva** / **turno** (`Reservation`) | Lugar de un usuario en una salida. Estados: activa, cumplida, cancelada, no-show. |
| **Ventana de reserva** | Período en el que se puede reservar una salida: de 30 a 2 min antes. |
| **Lista de espera** (`Waitlist`) | Solicitudes que no entraron en una salida llena, ordenadas por prioridad. |
| **Check-in** | Escaneo del QR del ascensor que confirma el uso de la reserva. |
| **No-show** | Reserva sin check-in válido a tiempo. |
| **Prioritario** | Usuario con prioridad aprobada y vigente: movilidad reducida (`REDUCED_MOBILITY`) o docente (`TEACHER`). |
| **Lugares reservados** | Hasta 2 de 10 lugares por salida en ascensores comunes, guardados para prioritarios. |
| **Envejecimiento** (aging) | Aumento de prioridad de una solicitud tras N min de espera. |
| **Edificio** (`Building`) | Lima (−4 a 10) o Independencia (−4 a 11). Agrupa núcleos. |
| **Núcleo** (`Core`) | Grupo de ascensores de un edificio: IND1, IND2, L1, L2, L3. Es el nodo del grafo de conexiones. |
| **Ascensor** (`Elevator`) | Cabina identificada por su código (01–08, 10–15, 33–36). Cada uno sirve su propia lista de pisos. |
| **Batería** | Ascensores de un mismo núcleo que sirven los mismos pisos (p. ej. L2 tiene una baja y una alta). |
| **Conexión** (`Connection`) | Paso entre núcleos válido en un rango de pisos, con su tiempo de caminata. |
| **Ascensor dedicado** | Ascensor exclusivo de un tipo de prioritario: IND1 05 (docentes) e IND1 06 (movilidad reducida). |
| **Ascensor accesible** | Ascensor apto para movilidad reducida: IND1 06. |
| **Certificado** | Documento que respalda la movilidad reducida. Estados: pendiente, aprobado (con vencimiento), rechazado. |
| **Revisor** (`REVIEWER`) | Rol que revisa certificados. |
