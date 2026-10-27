-- Migracion para bases PostgreSQL ya existentes.
-- Agrega las sesiones de aplicacion que respaldan el refresh token.
-- Ejecutar una sola vez:
--   docker exec -i sos-postgres psql -U sos_admin -d sosclinic < infra/migraciones/2026-10-27-sesiones.sql

CREATE TABLE IF NOT EXISTS sesion (
    id_sesion         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_personal       INT NOT NULL REFERENCES personal(id_personal) ON DELETE CASCADE,
    token_hash        VARCHAR(64) NOT NULL,
    fecha_creacion    TIMESTAMP NOT NULL DEFAULT now(),
    ultimo_uso        TIMESTAMP NOT NULL DEFAULT now(),
    fecha_expira      TIMESTAMP NOT NULL,
    fecha_maxima      TIMESTAMP NOT NULL,
    revocada          BOOLEAN NOT NULL DEFAULT FALSE,
    motivo_revocacion VARCHAR(40),
    ip_creacion       VARCHAR(45),
    user_agent        VARCHAR(200)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_sesion_token_hash ON sesion (token_hash);
CREATE INDEX IF NOT EXISTS ix_sesion_personal_activa
    ON sesion (id_personal) WHERE revocada = FALSE;
