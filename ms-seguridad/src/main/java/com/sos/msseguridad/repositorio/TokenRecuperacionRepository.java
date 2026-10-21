package com.sos.msseguridad.repositorio;

import com.sos.msseguridad.modelo.TokenRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Integer> {

    Optional<TokenRecuperacion> findByTokenHashAndUsadoFalse(String tokenHash);

    Optional<TokenRecuperacion> findTopByPersonal_IdPersonalAndTipoAndUsadoFalseOrderByIdTokenDesc(
            Integer idPersonal, String tipo);

    @Modifying
    @Transactional
    @Query("update TokenRecuperacion t set t.usado = true "
            + "where t.personal.idPersonal = :idPersonal and t.tipo = :tipo and t.usado = false")
    int invalidarPendientes(@Param("idPersonal") Integer idPersonal, @Param("tipo") String tipo);
}
