/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.dto;

public class LoginResponse {
    private String token;
    private String usuario;
    private String perfil;
    private Integer idPersonal;
    private java.util.List<ActividadDTO> actividades;
    private Boolean requiere2fa; // true = el login aun no termino, falta verificar el codigo

    public LoginResponse(String token, String usuario, String perfil, Integer idPersonal,
                          java.util.List<ActividadDTO> actividades, Boolean requiere2fa) {
        this.token = token; this.usuario = usuario; this.perfil = perfil;
        this.idPersonal = idPersonal; this.actividades = actividades; this.requiere2fa = requiere2fa;
    }
    public String getToken() { return token; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public Integer getIdPersonal() { return idPersonal; }
    public java.util.List<ActividadDTO> getActividades() { return actividades; }
    public Boolean getRequiere2fa() { return requiere2fa; }
}