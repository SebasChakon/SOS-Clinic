package com.sos.msseguridad.repositorio;

import com.sos.msseguridad.modelo.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface SesionRepository extends JpaRepository<Sesion, Integer> {

    Optional<Sesion> findByTokenHashAndRevocadaFalse(String tokenHash);

    @Modifying
    @Transactional
    @Query("update Sesion s set s.revocada = true, s.motivoRevocacion = :motivo "
            + "where s.personal.idPersonal = :idPersonal and s.revocada = false")
    int revocarTodas(@Param("idPersonal") Integer idPersonal, @Param("motivo") String motivo);
}
