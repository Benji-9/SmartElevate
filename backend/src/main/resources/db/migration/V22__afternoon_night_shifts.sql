-- Turnos del panel de administración: mañana, tarde y noche (docs/reglas/kpis.md#filtros).
-- El turno mañana pasa de 07:00–12:15 a 07:00–13:00 para que los tres turnos cubran todo el horario de
-- operación sin huecos. Hora de Buenos Aires, fin exclusivo.
UPDATE app_config SET config_value = '13:00' WHERE config_key = 'morning_shift_end';
INSERT INTO app_config (config_key, config_value, description) VALUES
    ('afternoon_shift_start', '13:00', 'Inicio del turno tarde (filtro de KPIs)'),
    ('afternoon_shift_end',   '18:00', 'Fin del turno tarde, exclusivo (filtro de KPIs)'),
    ('night_shift_start',     '18:00', 'Inicio del turno noche (filtro de KPIs)'),
    ('night_shift_end',       '22:30', 'Fin del turno noche, exclusivo (filtro de KPIs)');
