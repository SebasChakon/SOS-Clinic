INSERT INTO actividades (nombre_actividad, enlace)
SELECT 'enfermedades_crue', 'enfermedades-crue.html'
WHERE NOT EXISTS (
    SELECT 1 FROM actividades WHERE nombre_actividad = 'enfermedades_crue'
);

INSERT INTO perfil_actividad (id_perfil, id_actividad)
SELECT p.id_perfil, a.id_actividad
FROM perfil p
JOIN actividades a ON a.nombre_actividad = 'enfermedades_crue'
WHERE NOT EXISTS (
    SELECT 1
    FROM perfil_actividad pa
    WHERE pa.id_perfil = p.id_perfil
      AND pa.id_actividad = a.id_actividad
);