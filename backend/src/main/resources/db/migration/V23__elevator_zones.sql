-- Zonas de pisos por ascensor (ADR 0025, #339). La zona es una semilla: destinos que el ascensor prefiere cuando
-- su salida está vacía. Solo en ascensores comunes (IND1 05 y 06 no tienen). Valores a calibrar en el piloto.
-- La FK a elevator_floor garantiza que el ascensor pare en cada piso de su zona.
CREATE TABLE elevator_zone_floor (
    elevator_code VARCHAR(8) NOT NULL,
    floor_number  INT        NOT NULL,
    PRIMARY KEY (elevator_code, floor_number),
    FOREIGN KEY (elevator_code, floor_number) REFERENCES elevator_floor (elevator_code, floor_number)
);

-- Destinos válidos: −4 a 0 y 5 en adelante (regla de pisos bajos); los subsuelos van con el rango más bajo.
-- IND2 33: −4 a 0 y 5 a 8.
INSERT INTO elevator_zone_floor (elevator_code, floor_number)
SELECT e.code, f.n FROM elevator e
CROSS JOIN (VALUES (-4), (-3), (-2), (-1), (0), (5), (6), (7), (8)) AS f (n)
WHERE e.code = '33';

-- IND2 34: 9 a 11.
INSERT INTO elevator_zone_floor (elevator_code, floor_number)
SELECT e.code, f.n FROM elevator e
CROSS JOIN (VALUES (9), (10), (11)) AS f (n)
WHERE e.code = '34';

-- L3 10 y 11: −3 a 0 y 5 a 6.
INSERT INTO elevator_zone_floor (elevator_code, floor_number)
SELECT e.code, f.n FROM elevator e
CROSS JOIN (VALUES (-3), (-2), (-1), (0), (5), (6)) AS f (n)
WHERE e.code IN ('10', '11');

-- L3 12 y 13: 7 a 8.
INSERT INTO elevator_zone_floor (elevator_code, floor_number)
SELECT e.code, f.n FROM elevator e
CROSS JOIN (VALUES (7), (8)) AS f (n)
WHERE e.code IN ('12', '13');

-- L3 14 y 15: 9 a 10.
INSERT INTO elevator_zone_floor (elevator_code, floor_number)
SELECT e.code, f.n FROM elevator e
CROSS JOIN (VALUES (9), (10)) AS f (n)
WHERE e.code IN ('14', '15');

-- L2 baja 07 y 08: −4 a 0 y 5.
INSERT INTO elevator_zone_floor (elevator_code, floor_number)
SELECT e.code, f.n FROM elevator e
CROSS JOIN (VALUES (-4), (-3), (-2), (-1), (0), (5)) AS f (n)
WHERE e.code IN ('07', '08');

-- L2 alta 35: 6 a 8.
INSERT INTO elevator_zone_floor (elevator_code, floor_number)
SELECT e.code, f.n FROM elevator e
CROSS JOIN (VALUES (6), (7), (8)) AS f (n)
WHERE e.code = '35';

-- L2 alta 36: 9 a 10.
INSERT INTO elevator_zone_floor (elevator_code, floor_number)
SELECT e.code, f.n FROM elevator e
CROSS JOIN (VALUES (9), (10)) AS f (n)
WHERE e.code = '36';

-- L1 01 y 02: −3 a 0 y 5 a 6.
INSERT INTO elevator_zone_floor (elevator_code, floor_number)
SELECT e.code, f.n FROM elevator e
CROSS JOIN (VALUES (-3), (-2), (-1), (0), (5), (6)) AS f (n)
WHERE e.code IN ('01', '02');

-- L1 03 y 04: 7.
INSERT INTO elevator_zone_floor (elevator_code, floor_number)
SELECT e.code, f.n FROM elevator e
CROSS JOIN (VALUES (7)) AS f (n)
WHERE e.code IN ('03', '04');

-- A N minutos o menos de la salida la zona se abre y gana la de más lugares libres (ADR 0025).
-- Afinidad: una salida con reservas solo suma a quien va a esta distancia o menos de todos sus destinos.
INSERT INTO app_config (config_key, config_value, description) VALUES
    ('zone_open_to_any_before', 'PT5M', 'Cuánto antes de la salida se dejan de aplicar las zonas de pisos'),
    ('zone_affinity_max_floors', '2', 'Distancia máxima en pisos a cada destino reservado para sumarse a una salida');
