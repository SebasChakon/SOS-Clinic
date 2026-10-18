package com.sos.msseguridad.servicio;

/**
 * El servidor SMTP rechazo o no acepto el mensaje. Quien invoca decide
 * mostrar el aviso al usuario y reintentar el envio.
 */
public class EmailNoEnviadoException extends RuntimeException {

    public EmailNoEnviadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
