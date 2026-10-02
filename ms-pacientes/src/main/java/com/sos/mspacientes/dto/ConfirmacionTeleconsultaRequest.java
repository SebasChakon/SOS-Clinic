package com.sos.mspacientes.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfirmacionTeleconsultaRequest {

    @NotBlank
    private String fechaHora;

    @NotBlank
    private String enlace;

    public String getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(String fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getEnlace() {
        return enlace;
    }

    public void setEnlace(String enlace) {
        this.enlace = enlace;
    }
}