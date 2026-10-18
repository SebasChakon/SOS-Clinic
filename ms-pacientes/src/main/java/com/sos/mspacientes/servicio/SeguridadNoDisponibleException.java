package com.sos.mspacientes.servicio;

/** ms-seguridad no respondio o rechazo la programacion de la teleconsulta. */
public class SeguridadNoDisponibleException extends RuntimeException {

    public SeguridadNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public SeguridadNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
