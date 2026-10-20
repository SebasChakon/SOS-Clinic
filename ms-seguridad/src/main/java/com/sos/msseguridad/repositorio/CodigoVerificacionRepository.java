package com.sos.msseguridad.repositorio;

import com.sos.msseguridad.modelo.CodigoVerificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface CodigoVerificacionRepository extends JpaRepository<CodigoVerificacion, Integer> {

    Optional<CodigoVerificacion> findTopByPersonal_IdPersonalAndUsadoFalseOrderByIdCodigoDesc(Integer idPersonal);

    @Modifying
    @Transactional
    @Query("update CodigoVerificacion c set c.usado = true "
            + "where c.personal.idPersonal = :idPersonal and c.usado = false")
    int invalidarPendientes(@Param("idPersonal") Integer idPersonal);
}
