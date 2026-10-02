/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.dto;

// Version segura de Personal para listar/devolver -- NUNCA se expone la clave hasheada
public class PersonalResponse {
    private Integer idPersonal;
    private String nombre;
    private String apellido;
    private String email;
    private String cedula;
    private String telefono;
    private String usuario;
    private Integer idPerfil;
    private String perfil;
    private Integer idEstado;
    private Boolean requiere2fa;
// + getter/setter

    public PersonalResponse(Integer idPersonal, String nombre, String apellido, String email, String cedula, String telefono, String usuario, Integer idPerfil, String perfil, Integer idEstado, Boolean requiere2fa) {
        this.idPersonal = idPersonal;
        this.nombre = nombre;
        this.apellido = apellido;
        this.email = email;
        this.cedula = cedula;
        this.telefono = telefono;
        this.usuario = usuario;
        this.idPerfil = idPerfil;
        this.perfil = perfil;
        this.idEstado = idEstado;
        this.requiere2fa = requiere2fa;
    }

    public Integer getIdPersonal() { return idPersonal; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getEmail() { return email; }
    public String getCedula() { return cedula; }
    public String getTelefono() { return telefono; }
    public String getUsuario() { return usuario; }
    public Integer getIdPerfil() { return idPerfil; }
    public String getPerfil() { return perfil; }
    public Integer getIdEstado() { return idEstado; }
    public Boolean getRequiere2fa() {
        return requiere2fa;
    }
}