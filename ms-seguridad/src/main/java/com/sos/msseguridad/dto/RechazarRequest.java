package com.sos.msseguridad.dto;

import jakarta.validation.constraints.Size;

public class RechazarRequest {

    @Size(max = 100, message = "El motivo no puede superar 100 caracteres")
    private String motivo;

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
