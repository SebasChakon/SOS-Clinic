package com.sos.msseguridad.dto;

import jakarta.validation.constraints.NotBlank;

public class SolicitarDesactivar2faRequest {

    @NotBlank(message = "La clave actual es obligatoria")
    private String claveActual;

    public String getClaveActual() { return claveActual; }
    public void setClaveActual(String claveActual) { this.claveActual = claveActual; }
}
