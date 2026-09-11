/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.dto;

public class ActividadDTO {
    private String nombreActividad;
    private String enlace;

    public ActividadDTO(String nombreActividad, String enlace) {
        this.nombreActividad = nombreActividad;
        this.enlace = enlace;
    }

    public String getNombreActividad() { return nombreActividad; }
    public String getEnlace() { return enlace; }
}