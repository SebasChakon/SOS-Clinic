package com.sos.mspacientes.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfirmacionTeleconsultaRequest {

    @NotBlank
    private String fechaHora;

    @NotBlank
    private String enlace;

    /** ISO con zona horaria, ej. 2026-10-24T10:00:00-05:00 (opcional; si llega, se adjunta el .ics). */
    private String fechaHoraInicio;

    /** ISO con zona horaria de fin de la cita (opcional). */
    private String fechaHoraFin;

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

    public String getFechaHoraInicio() {
        return fechaHoraInicio;
    }

    public void setFechaHoraInicio(String fechaHoraInicio) {
        this.fechaHoraInicio = fechaHoraInicio;
    }

    public String getFechaHoraFin() {
        return fechaHoraFin;
    }

    public void setFechaHoraFin(String fechaHoraFin) {
        this.fechaHoraFin = fechaHoraFin;
    }
}
