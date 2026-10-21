/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.repositorio;

import com.sos.msseguridad.modelo.Personal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonalRepository extends JpaRepository<Personal, Integer> {

    Optional<Personal> findByUsuario(String usuario);

    Optional<Personal> findByUsuarioIgnoreCase(String usuario);

    Optional<Personal> findByEmailIgnoreCase(String email);

    boolean existsByUsuario(String usuario);

    boolean existsByUsuarioIgnoreCase(String usuario);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByCedula(String cedula);

    List<Personal> findByEstadoAprobacionOrderByIdPersonalAsc(String estadoAprobacion);
}
