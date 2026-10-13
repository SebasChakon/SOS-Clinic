BEGIN;

INSERT INTO actividades (nombre_actividad, enlace, id_estado)
SELECT 'dashboard_analitica', 'dashboard.html', 1
WHERE NOT EXISTS (
    SELECT 1 FROM actividades WHERE nombre_actividad = 'dashboard_analitica'
);

INSERT INTO perfil_actividad (id_perfil, id_actividad, id_estado)
SELECT p.id_perfil, a.id_actividad, 1
FROM perfil p
JOIN actividades a ON a.nombre_actividad = 'dashboard_analitica'
WHERE p.perfil = 'coordinacion'
  AND NOT EXISTS (
      SELECT 1
      FROM perfil_actividad pa
      WHERE pa.id_perfil = p.id_perfil
        AND pa.id_actividad = a.id_actividad
  );

COMMIT;
