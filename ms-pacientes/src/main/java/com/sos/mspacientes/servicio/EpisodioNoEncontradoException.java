package com.sos.mspacientes.servicio;

/** El episodio solicitado no existe en ms-triage. */
public class EpisodioNoEncontradoException extends RuntimeException {

    public EpisodioNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
