-- Notificaciones por email (#221).
-- Preferencias del usuario: por defecto activas (son avisos del servicio, no marketing); se apagan desde la app.
ALTER TABLE app_user ADD COLUMN notify_departure_reminder BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE app_user ADD COLUMN notify_elevator_out_of_service BOOLEAN DEFAULT TRUE NOT NULL;

-- Idempotencia del recordatorio: se marca al reclamarlo, antes de mandar el mail (a lo sumo un envío por reserva).
ALTER TABLE reservation ADD COLUMN reminder_sent_at TIMESTAMP WITH TIME ZONE;

-- Cuánto antes de la salida se manda el recordatorio. Valor propuesto, a calibrar en el piloto.
INSERT INTO app_config (config_key, config_value, description) VALUES
    ('departure_reminder_before', 'PT5M', 'Cuánto antes de la salida se manda el recordatorio por email');
