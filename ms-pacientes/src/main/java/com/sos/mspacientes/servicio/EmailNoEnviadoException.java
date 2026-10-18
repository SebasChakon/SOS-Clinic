package com.sos.mspacientes.servicio;

/**
 * El servidor SMTP rechazo o no acepto el mensaje. El alta del paciente no
 * debe verse afectada: quien invoca decide mostrar el aviso y reintentar.
 */
public class EmailNoEnviadoException extends RuntimeException {

    public EmailNoEnviadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
