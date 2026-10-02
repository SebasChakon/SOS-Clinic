/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.dto;

import java.time.LocalDateTime;

public class AuditoriaResponse {
    private Integer idLog;
    private String usuario;
    private String accion;
    private String detalle;
    private LocalDateTime fechaHora;

    public AuditoriaResponse(Integer idLog, String usuario, String accion, String detalle, LocalDateTime fechaHora) {
        this.idLog = idLog; this.usuario = usuario; this.accion = accion;
        this.detalle = detalle; this.fechaHora = fechaHora;
    }
    public Integer getIdLog() { return idLog; }
    public String getUsuario() { return usuario; }
    public String getAccion() { return accion; }
    public String getDetalle() { return detalle; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}