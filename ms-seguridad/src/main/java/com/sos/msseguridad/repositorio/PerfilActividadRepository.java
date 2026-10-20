/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.repositorio;

import com.sos.msseguridad.modelo.PerfilActividad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerfilActividadRepository extends JpaRepository<PerfilActividad, Integer> {

    List<PerfilActividad> findByPerfil_IdPerfil(Integer idPerfil);

    List<PerfilActividad> findByPerfil_IdPerfilAndIdEstado(Integer idPerfil, Integer idEstado);

    List<PerfilActividad> findByActividad_IdActividad(Integer idActividad);
}
