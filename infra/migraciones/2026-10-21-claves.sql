-- Migracion de seguridad de claves.
-- Agrega:
--   * aprobacion de cuentas por un segundo usuario con perfil seguridad
--   * activacion por correo y recuperacion de clave con token de un solo uso
--   * cambio obligatorio de clave temporal
--   * verificacion de cambio de correo
--   * challenge opaco para el segundo factor (2FA)
--
-- Ejecutar una sola vez sobre una base existente:
--   docker exec -i sos-postgres psql -U sos_admin -d sosclinic \
--     < infra/migraciones/2026-10-21-claves.sql

BEGIN;

ALTER TABLE personal
    ADD COLUMN IF NOT EXISTS debe_cambiar_clave BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS clave_expira TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS fecha_cambio_clave TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS estado_aprobacion VARCHAR(20) NOT NULL DEFAULT 'APROBADA',
    ADD COLUMN IF NOT EXISTS aprobado_por INT NULL REFERENCES personal(id_personal) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS fecha_aprobacion TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS email_pendiente VARCHAR(60) NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_personal_estado_aprobacion'
    ) THEN
        ALTER TABLE personal
            ADD CONSTRAINT ck_personal_estado_aprobacion
            CHECK (estado_aprobacion IN ('PENDIENTE', 'APROBADA', 'RECHAZADA'));
    END IF;
END $$;

-- El 2FA es obligatorio para los perfiles privilegiados, incluso en cuentas
-- que ya existian antes de esta migracion.
UPDATE personal p
SET requiere_2fa = TRUE
FROM perfil f
WHERE p.id_perfil = f.id_perfil
  AND lower(btrim(f.perfil)) IN ('administrador', 'seguridad');

-- Las cuentas que ya existian antes de este flujo tienen clave usable: se
-- marcan como activadas para que el administrador pueda enviarles un reset.
-- La condicion sobre token_recuperacion hace que el bloque solo actue en la
-- primera aplicacion sobre una base antigua, no en una base nueva ni al repetir.
DO $$
DECLARE primera_vez BOOLEAN;
BEGIN
    SELECT NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'token_recuperacion'
    ) INTO primera_vez;

    IF primera_vez THEN
        UPDATE personal
        SET fecha_cambio_clave = COALESCE(fecha_cambio_clave, now())
        WHERE estado_aprobacion = 'APROBADA';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS token_recuperacion (
    id_token       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_personal    INT NOT NULL REFERENCES personal(id_personal) ON DELETE CASCADE,
    tipo           VARCHAR(20) NOT NULL
                   CHECK (tipo IN ('RECUPERACION', 'ACTIVACION', 'CAMBIO_EMAIL')),
    token_hash     VARCHAR(64) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now(),
    fecha_expira   TIMESTAMP NOT NULL,
    usado          BOOLEAN NOT NULL DEFAULT FALSE,
    ip_solicitud   VARCHAR(45),
    user_agent     VARCHAR(200)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_token_recuperacion_hash
    ON token_recuperacion (token_hash);

CREATE INDEX IF NOT EXISTS ix_token_recuperacion_personal
    ON token_recuperacion (id_personal, tipo) WHERE usado = FALSE;

ALTER TABLE codigo_verificacion
    ADD COLUMN IF NOT EXISTS challenge_hash VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS proposito VARCHAR(20) NOT NULL DEFAULT 'LOGIN';

CREATE UNIQUE INDEX IF NOT EXISTS ux_codigo_challenge
    ON codigo_verificacion (challenge_hash) WHERE challenge_hash IS NOT NULL;

-- Actividad y permiso para aprobar cuentas. Solo el perfil seguridad la recibe.
-- La base puede venir de un esquema anterior sin el perfil seguridad.
INSERT INTO perfil (perfil)
SELECT 'seguridad'
WHERE NOT EXISTS (
    SELECT 1 FROM perfil WHERE lower(btrim(perfil)) = 'seguridad'
);

INSERT INTO actividades (nombre_actividad, enlace, id_estado)
SELECT 'aprobar_cuentas', 'aprobaciones.html', 1
WHERE NOT EXISTS (
    SELECT 1 FROM actividades WHERE lower(btrim(enlace)) = 'aprobaciones.html'
);

INSERT INTO perfil_actividad (id_perfil, id_actividad, id_estado)
SELECT p.id_perfil, a.id_actividad, 1
FROM perfil p
CROSS JOIN actividades a
WHERE lower(btrim(p.perfil)) = 'seguridad'
  AND lower(btrim(a.enlace)) = 'aprobaciones.html'
  AND NOT EXISTS (
      SELECT 1 FROM perfil_actividad pa
      WHERE pa.id_perfil = p.id_perfil
        AND pa.id_actividad = a.id_actividad
  );

COMMIT;
