/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.dto;

import jakarta.validation.constraints.NotNull;

public class PerfilActividadRequest {
    @NotNull(message = "El id del perfil es obligatorio")
    private Integer idPerfil;
    @NotNull(message = "El id de la actividad es obligatorio")
    private Integer idActividad;

    public Integer getIdPerfil() { return idPerfil; }
    public void setIdPerfil(Integer idPerfil) { this.idPerfil = idPerfil; }
    public Integer getIdActividad() { return idActividad; }
    public void setIdActividad(Integer idActividad) { this.idActividad = idActividad; }
}