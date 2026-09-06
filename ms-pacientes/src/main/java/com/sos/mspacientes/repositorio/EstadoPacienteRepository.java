/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mspacientes.repositorio;

import com.sos.mspacientes.modelo.EstadoPaciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstadoPacienteRepository extends JpaRepository<EstadoPaciente, Integer> {
    List<EstadoPaciente> findByIdRegistro(Integer idRegistro);
}
