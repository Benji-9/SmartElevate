-- Decisiones de reglas del 14/10 (#218, turnos.md §3 y §4, usuarios-y-prioridad.md).

-- Cancelar sin falta hasta 10 min antes de la salida (era 1 min). Calibrable en el piloto.
UPDATE app_config SET config_value = 'PT10M' WHERE config_key = 'cancellation_deadline_before';

-- La prioridad de docente vence al fin del cuatrimestre en curso, no tras una duración fija.
DELETE FROM app_config WHERE config_key = 'priority_teacher_validity';

-- Lista de fechas de cierre, más larga que los valores de hasta ahora.
ALTER TABLE app_config ALTER COLUMN config_value SET DATA TYPE VARCHAR(255);

-- Cierres de cuatrimestre: fechas ISO (yyyy-MM-dd, hora de Buenos Aires) separadas por coma, en orden.
-- Un docente aprobado vence al terminar el primer cierre que sea hoy o después. Si no queda ninguno, la
-- aprobación falla: hay que agregar el próximo cierre, p. ej.
-- UPDATE app_config SET config_value = '2026-12-31,2027-07-31' WHERE config_key = 'semester_end_dates';
-- PROVISORIO: 2026-12-31 hasta confirmar la fecha real con el calendario académico de UADE.
INSERT INTO app_config (config_key, config_value, description) VALUES
    ('semester_end_dates', '2026-12-31',
     'Cierres de cuatrimestre (yyyy-MM-dd, separados por coma): la prioridad de docente vence al fin de ese día');

-- Backfill: las aprobaciones de docente previas no vencían (expires_at NULL). Vencen al fin del primer cierre de
-- arriba: fin del 2026-12-31 en Buenos Aires = 2027-01-01 03:00 UTC.
UPDATE user_priority SET expires_at = TIMESTAMP WITH TIME ZONE '2027-01-01 03:00:00+00'
    WHERE category = 'TEACHER' AND status = 'APPROVED' AND expires_at IS NULL;
