package com.sos.mspacientes.dto;

/**
 * Estado final de cada correo de la notificacion de alta.
 * estados: ENVIADO, FALLIDO u OMITIDO (por ejemplo, paciente sin correo).
 */
public record ResultadoEnvioCorreo(String tipo, String estado, String detalle) {

    public static final String ENVIADO = "ENVIADO";
    public static final String FALLIDO = "FALLIDO";
    public static final String OMITIDO = "OMITIDO";
}
