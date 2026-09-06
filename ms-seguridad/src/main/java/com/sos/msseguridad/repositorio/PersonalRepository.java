/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.repositorio;

import com.sos.msseguridad.modelo.Personal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonalRepository extends JpaRepository<Personal, Integer> {

    Optional<Personal> findByUsuario(String usuario);

    boolean existsByUsuario(String usuario);

    boolean existsByEmail(String email);

    boolean existsByCedula(String cedula);
}
