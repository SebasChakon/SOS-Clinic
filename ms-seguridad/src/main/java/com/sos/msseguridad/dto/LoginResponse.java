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

    public LoginResponse(String token, String usuario, String perfil, Integer idPersonal) {
        this.token = token;
        this.usuario = usuario;
        this.perfil = perfil;
        this.idPersonal = idPersonal;
    }

    public String getToken() { return token; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public Integer getIdPersonal() { return idPersonal; }
}
