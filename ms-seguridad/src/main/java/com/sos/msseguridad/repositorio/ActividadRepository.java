package com.sos.msseguridad.repositorio;

import com.sos.msseguridad.modelo.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActividadRepository extends JpaRepository<Actividad, Integer> {

    boolean existsByNombreActividadIgnoreCase(String nombreActividad);

    boolean existsByNombreActividadIgnoreCaseAndIdActividadNot(String nombreActividad,
                                                                Integer idActividad);

    boolean existsByEnlaceIgnoreCase(String enlace);

    boolean existsByEnlaceIgnoreCaseAndIdActividadNot(String enlace, Integer idActividad);
}
