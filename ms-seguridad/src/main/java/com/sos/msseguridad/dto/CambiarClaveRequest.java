package com.sos.msseguridad.dto;

import jakarta.validation.constraints.NotBlank;

public class CambiarClaveRequest {

    @NotBlank(message = "La clave actual es obligatoria")
    private String claveActual;

    @NotBlank(message = "La clave nueva es obligatoria")
    private String claveNueva;

    public String getClaveActual() { return claveActual; }
    public void setClaveActual(String claveActual) { this.claveActual = claveActual; }
    public String getClaveNueva() { return claveNueva; }
    public void setClaveNueva(String claveNueva) { this.claveNueva = claveNueva; }
}
