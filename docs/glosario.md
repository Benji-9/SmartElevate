# Glosario

Términos del dominio, para que backend, frontend y docs hablen igual. Entre paréntesis, el nombre sugerido en código.

| Término | Significado |
| --- | --- |
| **Salida** / **franja** (`Departure`) | Viaje programado de un ascensor, cada ~2 min. Es la unidad que se reserva. Capacidad: 10. |
| **Reserva** / **turno** (`Reservation`) | Lugar de un usuario en una salida. Estados: activa (`ACTIVE`), cancelada sin falta (`CANCELLED`), cancelación tardía (`CANCELLED_LATE`), usada (`USED`, check-in a tiempo) y no-show (`NO_SHOW`). |
| **Usada** (`USED`) | Reserva con check-in a tiempo, en su ascensor o en otro. |
| **No-show** (`NO_SHOW`) | Estado de la reserva sin check-in a tiempo (a los 60 s de la salida) o con check-in fuera de hora. Cuenta como falta. |
| **Falta** | No-show (`NO_SHOW`) o cancelación tardía (`CANCELLED_LATE`). Se fecha a la hora de salida de la reserva. 3 faltas en 7 días suspenden 24 h, salvo a los prioritarios. |
| **Suspensión** (`NO_SHOW_SUSPENDED`) | 24 h sin poder reservar desde la falta que completa 3 en 7 días. Reservar responde con ese `code`; el estado se consulta en `GET /api/me/no-shows`. |
| **Cancelación tardía** (`CANCELLED_LATE`) | Cancelar pasado el límite sin falta (10 min antes de la salida) y antes de la salida. Libera el lugar, pero cuenta como falta y no devuelve el cupo diario. Después de la salida no se puede cancelar. |
| **Ventana de reserva** | Período en el que se puede reservar una salida: de 30 a 2 min antes. |
| **Lista de espera** (`Waitlist`) | Solicitudes que no entraron en una salida llena, ordenadas por prioridad. **Fuera del MVP** ([alcance](alcance-mvp.md)). |
| **Check-in** (`CheckIn`) | Escaneo del QR del ascensor que confirma el uso de la reserva. Uno por reserva. Resultados: cumplida (`ON_TIME`), otro ascensor (`OTHER_ELEVATOR`) y fuera de hora (`LATE`, cuenta como no-show). Origen (`source`): kiosco real (`REAL`) o simulador de admin (`SIMULATED`). |
| **Kiosco QR** | Página web `/qr/:ascensor`, abierta en una tablet o pantalla del hall, que muestra el QR rotativo de un ascensor ([ADR 0016](adr/0016-qr-firmado-con-hmac-y-kiosco-web.md)). |
| **Prioritario** | Usuario con prioridad aprobada y vigente: movilidad reducida (`REDUCED_MOBILITY`) o docente (`TEACHER`). |
| **Tipo de usuario declarado** (`declaredUserType`) | Lo que la persona elige al registrarse: Estudiante (`STUDENT`) o Docente (`TEACHER`). No otorga nada; un docente declarado espera la aprobación de un ADMIN. |
| **Lugares reservados** | Hasta 2 de 10 lugares por salida en ascensores comunes, guardados para prioritarios. |
| **Envejecimiento** (aging) | Aumento de prioridad de una solicitud que espera. **Fuera del MVP** ([#212](https://github.com/Benji-9/SmartElevate/issues/212)). |
| **Edificio** (`Building`) | Lima (−4 a 10) o Independencia (−4 a 11). Agrupa núcleos. |
| **Núcleo** (`Core`) | Grupo de ascensores de un edificio: IND1, IND2, L1, L2, L3. Es el nodo del grafo de conexiones. |
| **Ascensor** (`Elevator`) | Cabina identificada por su código (01–08, 10–15, 33–36). Cada uno sirve su propia lista de pisos. |
| **Batería** | Ascensores de un mismo núcleo que sirven los mismos pisos (p. ej. L2 tiene una baja y una alta). |
| **Conexión** (`Connection`) | Paso entre núcleos válido en un rango de pisos, con su tiempo de caminata (de punta a punta, ~45–70 s). |
| **Ascensor dedicado** | Ascensor exclusivo de un tipo de prioritario: IND1 05 (docentes) e IND1 06 (movilidad reducida). |
| **Ascensor accesible** | Ascensor apto para movilidad reducida: IND1 06. |
| **Certificado** | Documento que respalda la movilidad reducida. Estados: pendiente (`PENDING`), aprobado con vencimiento (`APPROVED`), rechazado (`REJECTED`). |
| **Revisor** (`REVIEWER`) | Rol que revisa certificados. |
