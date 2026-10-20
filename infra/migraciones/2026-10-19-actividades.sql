-- Limpieza de actividades duplicadas por enlace y restricciones de unicidad.
-- Mantiene la actividad con el id mas bajo como canonica, reasigna las
-- asignaciones que no existan y elimina las duplicadas.
--
-- Ejecutar una sola vez:
--   docker exec -i sos-postgres psql -U sos_admin -d sosclinic \
--     < infra/migraciones/2026-10-19-actividades.sql

BEGIN;

CREATE TEMP TABLE actividades_duplicadas ON COMMIT DROP AS
SELECT id_actividad,
       MIN(id_actividad) OVER (PARTITION BY LOWER(btrim(enlace))) AS id_canonico
FROM actividades
WHERE enlace IS NOT NULL AND btrim(enlace) <> '';

INSERT INTO perfil_actividad (id_perfil, id_actividad, id_estado)
SELECT pa.id_perfil, d.id_canonico, pa.id_estado
FROM perfil_actividad pa
JOIN actividades_duplicadas d ON d.id_actividad = pa.id_actividad
WHERE d.id_actividad <> d.id_canonico
ON CONFLICT (id_perfil, id_actividad) DO NOTHING;

DELETE FROM perfil_actividad pa
USING actividades_duplicadas d
WHERE pa.id_actividad = d.id_actividad
  AND d.id_actividad <> d.id_canonico;

DELETE FROM actividades a
USING actividades_duplicadas d
WHERE a.id_actividad = d.id_actividad
  AND d.id_actividad <> d.id_canonico;

CREATE UNIQUE INDEX IF NOT EXISTS ux_actividades_nombre_lower
    ON actividades (LOWER(btrim(nombre_actividad)));

CREATE UNIQUE INDEX IF NOT EXISTS ux_actividades_enlace_lower
    ON actividades (LOWER(btrim(enlace)))
    WHERE enlace IS NOT NULL AND btrim(enlace) <> '';

COMMIT;
