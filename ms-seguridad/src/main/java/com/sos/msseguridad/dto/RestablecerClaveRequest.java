package com.sos.msseguridad.dto;

import jakarta.validation.constraints.NotBlank;

public class RestablecerClaveRequest {

    @NotBlank(message = "El token es obligatorio")
    private String token;

    @NotBlank(message = "La clave nueva es obligatoria")
    private String clave;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
}
