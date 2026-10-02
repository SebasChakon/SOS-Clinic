package com.sos.msseguridad.dto;

import jakarta.validation.constraints.NotBlank;

public class ProgramarTeleconsultaRequest {

    @NotBlank(message = "El nombre del paciente es obligatorio")
    private String nombrePaciente;

    public String getNombrePaciente() {
        return nombrePaciente;
    }

    public void setNombrePaciente(String nombrePaciente) {
        this.nombrePaciente = nombrePaciente;
    }
}