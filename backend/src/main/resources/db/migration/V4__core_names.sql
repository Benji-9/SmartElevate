-- Nombre visible de cada núcleo (#133): la UI lo muestra tal cual o abreviado ("Indep. 1").
ALTER TABLE core ADD COLUMN name VARCHAR(64);
UPDATE core SET name = 'Independencia 2' WHERE code = 'IND2';
UPDATE core SET name = 'Independencia 1' WHERE code = 'IND1';
UPDATE core SET name = 'Lima 3' WHERE code = 'L3';
UPDATE core SET name = 'Lima 2' WHERE code = 'L2';
UPDATE core SET name = 'Lima 1' WHERE code = 'L1';
ALTER TABLE core ALTER COLUMN name SET NOT NULL;
