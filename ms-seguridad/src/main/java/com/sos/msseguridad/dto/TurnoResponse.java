/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.dto;

import java.time.LocalTime;

public class TurnoResponse {
    private Integer idTurno;
    private Integer idPersonal;
    private String usuario;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private Integer idEstado;

    public TurnoResponse(Integer idTurno, Integer idPersonal, String usuario, LocalTime horaInicio, LocalTime horaFin, Integer idEstado) {
        this.idTurno = idTurno; this.idPersonal = idPersonal; this.usuario = usuario;
        this.horaInicio = horaInicio; this.horaFin = horaFin; this.idEstado = idEstado;
    }
    public Integer getIdTurno() { return idTurno; }
    public Integer getIdPersonal() { return idPersonal; }
    public String getUsuario() { return usuario; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraFin() { return horaFin; }
    public Integer getIdEstado() { return idEstado; }
}
