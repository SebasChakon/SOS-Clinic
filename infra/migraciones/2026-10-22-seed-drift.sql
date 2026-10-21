-- Migracion para alinear una base existente con las entidades JPA y con el
-- seed canonico. Es idempotente: se puede ejecutar mas de una vez.
--
--   docker exec -i sos-postgres psql -U sos_admin -d sosclinic \
--     < infra/migraciones/2026-10-22-seed-drift.sql

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE paciente
    ADD COLUMN IF NOT EXISTS email VARCHAR(80);
ALTER TABLE paciente
    ALTER COLUMN email TYPE VARCHAR(80);

ALTER TABLE registro_triage
    ADD COLUMN IF NOT EXISTS diagnostico_final VARCHAR(1000);
ALTER TABLE registro_triage
    ALTER COLUMN diagnostico_final TYPE VARCHAR(1000);

ALTER TABLE registro_triage
    ADD COLUMN IF NOT EXISTS tratamiento VARCHAR(1000);
ALTER TABLE registro_triage
    ALTER COLUMN tratamiento TYPE VARCHAR(1000);

COMMIT;
