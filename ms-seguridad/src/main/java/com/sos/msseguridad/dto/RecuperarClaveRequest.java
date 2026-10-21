package com.sos.msseguridad.dto;

import jakarta.validation.constraints.NotBlank;

public class RecuperarClaveRequest {

    @NotBlank(message = "Ingresa tu usuario o correo")
    private String identificador;

    public String getIdentificador() { return identificador; }
    public void setIdentificador(String identificador) { this.identificador = identificador; }
}
