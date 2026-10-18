package com.sos.mspacientes.servicio;

/** ms-triage no respondio (apagado, timeout o error interno). */
public class TriageNoDisponibleException extends RuntimeException {

    public TriageNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
