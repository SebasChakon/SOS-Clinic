/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mstriage.repositorio;

import com.sos.mstriage.modelo.RegistroTriage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RegistroTriageRepository extends JpaRepository<RegistroTriage, Integer> {

    // Lista de priorizacion: ordenada por prioridad de categoria (menor numero = mas urgente)
    // y dentro de la misma prioridad, por quien llego primero
    @Query("SELECT r FROM RegistroTriage r " +
           "WHERE r.estadoTriage.nombreEstado <> 'Atendido' " +
           "ORDER BY r.categoriaTriage.prioridad ASC, r.fechaHoraLlegada ASC")
    List<RegistroTriage> listaPriorizacion();

    List<RegistroTriage> findByIdPaciente(Integer idPaciente);
}
