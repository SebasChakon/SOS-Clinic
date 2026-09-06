/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mstriage.dto;

import jakarta.validation.constraints.NotNull;

public class RegistroTriageRequest {

    @NotNull(message = "El id del paciente es obligatorio")
    private Integer idPaciente;

    @NotNull(message = "La categoria de triage es obligatoria")
    private Integer idCategoriaTriage;

    private String ubicacion;

    public Integer getIdPaciente() { return idPaciente; }
    public void setIdPaciente(Integer idPaciente) { this.idPaciente = idPaciente; }
    public Integer getIdCategoriaTriage() { return idCategoriaTriage; }
    public void setIdCategoriaTriage(Integer idCategoriaTriage) { this.idCategoriaTriage = idCategoriaTriage; }
    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
}
