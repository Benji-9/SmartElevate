-- Decisiones de reglas tomadas en #113 (docs/reglas/turnos.md#decisiones-tomadas).
-- Los lugares prioritarios se liberan antes del cierre de la ventana de reserva (2 min), así alguien los puede tomar.
UPDATE app_config SET config_value = 'PT3M' WHERE config_key = 'priority_seats_release_before';
UPDATE app_config SET config_value = 'PT60S' WHERE config_key = 'qr_rotation';
UPDATE app_config SET config_value = '4',
    description = 'Pisos bajos: no se reserva con destino entre el 1 y este piso (movilidad reducida exenta)'
    WHERE config_key = 'low_floors_threshold';
