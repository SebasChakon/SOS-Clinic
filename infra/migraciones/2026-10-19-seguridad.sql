-- Migracion para bases PostgreSQL ya existentes.
-- Aplica los cambios necesarios para el hash BCrypt del codigo 2FA.
-- Ejecutar una sola vez:
--   docker exec -i sos-postgres psql -U sos_admin -d sosclinic < infra/migraciones/2026-10-19-seguridad.sql

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'codigo_verificacion'
    ) THEN
        ALTER TABLE codigo_verificacion
            ALTER COLUMN codigo TYPE VARCHAR(255);
    ELSE
        CREATE TABLE codigo_verificacion (
            id_codigo    INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            id_personal  INT NOT NULL REFERENCES personal(id_personal) ON DELETE CASCADE,
            codigo       VARCHAR(255) NOT NULL,
            fecha_expira TIMESTAMP NOT NULL,
            usado        BOOLEAN NOT NULL DEFAULT FALSE
        );
    END IF;
END $$;

ALTER TABLE personal
    ADD COLUMN IF NOT EXISTS requiere_2fa BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS turno (
    id_turno     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_personal  INT NOT NULL REFERENCES personal(id_personal) ON DELETE CASCADE,
    hora_inicio  TIME NOT NULL,
    hora_fin     TIME NOT NULL,
    id_estado    INT NOT NULL DEFAULT 1 CHECK (id_estado IN (0, 1))
);
