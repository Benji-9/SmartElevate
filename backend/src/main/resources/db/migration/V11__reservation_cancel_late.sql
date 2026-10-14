-- Cancelar una reserva (#116): estado CANCELLED_LATE (cancelada pasado el límite sin falta, cuenta como falta).
-- El CHECK de V7 no tiene nombre (y H2 y Postgres le ponen nombres distintos), así que se reemplaza la columna:
-- al borrarla se borra su CHECK en los dos motores.
ALTER TABLE reservation ADD COLUMN status_new VARCHAR(16);
UPDATE reservation SET status_new = status;
ALTER TABLE reservation DROP COLUMN status;
ALTER TABLE reservation RENAME COLUMN status_new TO status;
ALTER TABLE reservation ALTER COLUMN status SET NOT NULL;
ALTER TABLE reservation ADD CONSTRAINT ck_reservation_status
    CHECK (status IN ('ACTIVE', 'CANCELLED', 'CANCELLED_LATE'));
-- Solo una reserva activa ocupa lugar: cancelar tiene que liberar el lugar (seat_number NULL).
ALTER TABLE reservation ADD CONSTRAINT ck_reservation_seat_when_active
    CHECK ((status = 'ACTIVE') = (seat_number IS NOT NULL));
