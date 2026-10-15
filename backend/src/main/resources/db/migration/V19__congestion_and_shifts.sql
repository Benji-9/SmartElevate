-- Congestión y KPIs (#220). Ocupación = lugares reservados (o usados, en el panel) / capacidad, en %.
-- Las reglas no fijan los umbrales del nivel: 60 y 85 son una propuesta (cuadran con los ejemplos de
-- docs/frontend/SCREENS.md) y quedan a calibrar en el piloto.
-- El turno de cursada es el de docs/reglas/kpis.md#filtros (hora de Buenos Aires, fin exclusivo).
INSERT INTO app_config (config_key, config_value, description) VALUES
    ('congestion_medium_from', '60',    'Ocupación (%) desde la que un núcleo tiene congestión media (a calibrar en el piloto)'),
    ('congestion_high_from',   '85',    'Ocupación (%) desde la que un núcleo tiene congestión alta (a calibrar en el piloto)'),
    ('morning_shift_start',    '07:00', 'Inicio del turno mañana de cursada (filtro de KPIs)'),
    ('morning_shift_end',      '12:15', 'Fin del turno mañana de cursada, exclusivo (filtro de KPIs)');
