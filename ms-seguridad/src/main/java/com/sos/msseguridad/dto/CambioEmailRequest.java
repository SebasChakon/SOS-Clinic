package com.sos.msseguridad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class CambioEmailRequest {

    @NotBlank(message = "La clave actual es obligatoria")
    private String claveActual;

    @NotBlank(message = "El correo nuevo es obligatorio")
    @Email(message = "El correo nuevo no tiene un formato valido")
    private String emailNuevo;

    public String getClaveActual() { return claveActual; }
    public void setClaveActual(String claveActual) { this.claveActual = claveActual; }
    public String getEmailNuevo() { return emailNuevo; }
    public void setEmailNuevo(String emailNuevo) { this.emailNuevo = emailNuevo; }
}
