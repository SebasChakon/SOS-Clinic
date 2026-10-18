package com.sos.msseguridad.dto;

public class TeleconsultaResponse {

    private final String fechaHora;
    private final String enlace;
    /** ISO con zona horaria, ej. 2026-10-24T10:00-05:00 (para el archivo de calendario). */
    private final String fechaHoraInicio;
    private final String fechaHoraFin;

    public TeleconsultaResponse(String fechaHora, String enlace) {
        this(fechaHora, enlace, null, null);
    }

    public TeleconsultaResponse(String fechaHora, String enlace,
                                String fechaHoraInicio, String fechaHoraFin) {
        this.fechaHora = fechaHora;
        this.enlace = enlace;
        this.fechaHoraInicio = fechaHoraInicio;
        this.fechaHoraFin = fechaHoraFin;
    }

    public String getFechaHora() {
        return fechaHora;
    }

    public String getEnlace() {
        return enlace;
    }

    public String getFechaHoraInicio() {
        return fechaHoraInicio;
    }

    public String getFechaHoraFin() {
        return fechaHoraFin;
    }
}
