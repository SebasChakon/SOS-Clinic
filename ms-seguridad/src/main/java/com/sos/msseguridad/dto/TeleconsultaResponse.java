package com.sos.msseguridad.dto;

public class TeleconsultaResponse {

    private final String fechaHora;
    private final String enlace;

    public TeleconsultaResponse(String fechaHora, String enlace) {
        this.fechaHora = fechaHora;
        this.enlace = enlace;
    }

    public String getFechaHora() {
        return fechaHora;
    }

    public String getEnlace() {
        return enlace;
    }
}