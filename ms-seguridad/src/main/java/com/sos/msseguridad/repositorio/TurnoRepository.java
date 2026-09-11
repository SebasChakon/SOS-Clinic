/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.repositorio;

import com.sos.msseguridad.modelo.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Integer> {
    List<Turno> findByPersonal_IdPersonalAndIdEstado(Integer idPersonal, Integer idEstado);
}
