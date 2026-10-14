-- Cupo prioritario (#117, turnos.md sección 6). Cada reserva guarda si el usuario tenía una prioridad vigente al
-- reservar (se evalúa en el servidor, ADR 0009). Con eso se sabe cuántos de los lugares reservados para prioritarios
-- de una salida ya se usaron, aunque la prioridad venza o se revoque después.
ALTER TABLE reservation ADD COLUMN priority_user BOOLEAN NOT NULL DEFAULT FALSE;
