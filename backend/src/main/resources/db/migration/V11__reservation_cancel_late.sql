-- Cancelar una reserva (#116): estado CANCELLED_LATE (cancelada pasado el límite sin falta, cuenta como falta).
-- El CHECK de V7 no tiene nombre (y H2 y Postgres le ponen nombres distintos), así que se reemplaza la columna:
-- al borrarla se borra su CHECK en los dos motores. Los valores se copian antes de borrar la vieja, y el NOT NULL
-- posterior falla (y Flyway revierte) si alguna fila quedara sin estado. Lo prueba FlywayV11MigrationTest.
ALTER TABLE reservation ADD COLUMN status_new VARCHAR(16);
UPDATE reservation SET status_new = status;
ALTER TABLE reservation DROP COLUMN status;
ALTER TABLE reservation RENAME COLUMN status_new TO status;
ALTER TABLE reservation ALTER COLUMN status SET NOT NULL;
-- V7 permitía una cancelada con lugar (la app nunca la creó): se libera para cumplir el CHECK de abajo.
UPDATE reservation SET seat_number = NULL WHERE status <> 'ACTIVE';
ALTER TABLE reservation ADD CONSTRAINT ck_reservation_status
    CHECK (status IN ('ACTIVE', 'CANCELLED', 'CANCELLED_LATE'));
-- Solo una reserva activa ocupa lugar: cancelar tiene que liberar el lugar (seat_number NULL).
ALTER TABLE reservation ADD CONSTRAINT ck_reservation_seat_when_active
    CHECK ((status = 'ACTIVE') = (seat_number IS NOT NULL));
