package com.sos.msseguridad.dto;

import jakarta.validation.constraints.NotBlank;

public class ValidarTokenRequest {

    @NotBlank(message = "El token es obligatorio")
    private String token;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
