-- Parámetros de negocio (ADR 0010): clave/valor, validados por ConfigService al arrancar la app.
-- Formatos: duraciones en ISO-8601 (PT2M = 2 min, PT60S = 60 s, P7D = 7 días), horas como HH:mm
-- (hora de Buenos Aires) y enteros sin unidad. NULL = parámetro todavía sin definir.
-- Para probar una variante: UPDATE app_config SET config_value = 'PT3M' WHERE config_key = 'slot_duration';
-- y reiniciar la app (los valores se leen y validan al arrancar).
CREATE TABLE app_config (
    config_key   VARCHAR(64)  NOT NULL PRIMARY KEY,
    config_value VARCHAR(64),
    description  VARCHAR(255) NOT NULL
);

INSERT INTO app_config (config_key, config_value, description) VALUES
    ('operating_hours_start',         '07:00', 'Inicio del horario de operación'),
    ('operating_hours_end',           '22:30', 'Fin del horario de operación'),
    ('slot_duration',                 'PT2M',  'Duración de una franja (una salida de ascensor)'),
    ('turn_capacity',                 '10',    'Lugares por salida'),
    ('booking_opens_before',          'PT30M', 'Apertura de la reserva antes de la salida (solo mismo día)'),
    ('booking_closes_before',         'PT2M',  'Cierre de la reserva antes de la salida'),
    ('max_active_bookings_per_user',  '1',     'Reservas activas a la vez por usuario'),
    ('max_daily_bookings_per_user',   '8',     'Reservas por día por usuario'),
    ('cancellation_deadline_before',  'PT1M',  'Límite para cancelar sin falta, antes de la salida'),
    ('cancellation_wait_tolerance',   NULL,    'Demora sobre la espera estimada que permite cancelar sin falta (a definir)'),
    ('check_in_opens_before',         'PT1M',  'Apertura de la ventana de check-in antes de la salida'),
    ('check_in_closes_after',         'PT2M',  'Cierre de la ventana de check-in después de la salida'),
    ('no_show_after',                 'PT60S', 'Sin check-in a este tiempo de la salida, la reserva es no-show'),
    ('no_show_suspension_threshold',  '3',     'No-shows que suspenden al usuario (no aplica a prioritarios)'),
    ('no_show_suspension_window',     'P7D',   'Período en el que se cuentan los no-shows'),
    ('no_show_suspension_duration',   'PT24H', 'Duración de la suspensión'),
    ('priority_seats_per_departure',  '2',     'Lugares reservados para prioritarios en ascensores comunes'),
    ('priority_seats_release_before', 'PT1M',  'Liberación de los lugares prioritarios no usados, antes de la salida'),
    ('priority_aging',                NULL,    'Envejecimiento de prioridad (a definir)'),
    ('qr_rotation',                   'PT30S', 'Período de rotación del QR del ascensor'),
    ('boarding_time',                 'PT14S', 'Tiempo de carga de 10 personas (medido)'),
    ('time_per_floor',                'PT3S',  'Tiempo de viaje por piso (medido)'),
    ('low_floors_threshold',          NULL,    'Umbral de pisos bajos (a confirmar en #22)');
