package com.sos.msseguridad.dto;

import jakarta.validation.constraints.NotBlank;

public class Desactivar2faRequest {

    @NotBlank(message = "El challenge es obligatorio")
    private String challenge;

    @NotBlank(message = "El codigo es obligatorio")
    private String codigo;

    public String getChallenge() { return challenge; }
    public void setChallenge(String challenge) { this.challenge = challenge; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
}
