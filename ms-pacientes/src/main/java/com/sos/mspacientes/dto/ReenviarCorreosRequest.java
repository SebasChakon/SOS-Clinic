package com.sos.mspacientes.dto;

/**
 * Reintento de los correos de un episodio ya dado de alta: solo se
 * reenvian notificaciones, el alta no se vuelve a tocar.
 */
public class ReenviarCorreosRequest {

    /** Direccion del paciente para las farmacias cercanas del correo. */
    private String direccion;

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
}
