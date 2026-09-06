/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mstriage.repositorio;

import com.sos.mstriage.modelo.EstadoTriage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoTriageRepository extends JpaRepository<EstadoTriage, Integer> {
    Optional<EstadoTriage> findByNombreEstado(String nombreEstado);
}