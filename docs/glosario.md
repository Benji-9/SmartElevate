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
| **Edificio** (`Building`) | Lima (−2 a 10) o Independencia (−3 a 10). |
| **Ascensor** (`Elevator`) | IND1, IND2, L1, L2, L3… Cada uno sirve un conjunto de pisos. |
| **Conexión** (`Connection`) | Paso entre edificios válido en un rango de pisos, con su tiempo de caminata. |
| **Ascensor accesible** | Ascensor apto para movilidad reducida; existe en un solo edificio. |
| **Certificado** | Documento que respalda la movilidad reducida. Estados: pendiente, aprobado (con vencimiento), rechazado. |
| **Revisor** (`REVIEWER`) | Rol que revisa certificados. |
