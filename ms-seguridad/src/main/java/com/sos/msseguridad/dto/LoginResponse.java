/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.dto;

import java.util.List;

public class LoginResponse {

    private String token;
    private String usuario;
    private String perfil;
    private Integer idPersonal;
    private List<ActividadDTO> actividades;

    public LoginResponse(String token, String usuario, String perfil, Integer idPersonal, List<ActividadDTO> actividades) {
        this.token = token;
        this.usuario = usuario;
        this.perfil = perfil;
        this.idPersonal = idPersonal;
        this.actividades = actividades;
    }

    public String getToken() { return token; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public Integer getIdPersonal() { return idPersonal; }
    public List<ActividadDTO> getActividades() { return actividades; }
}