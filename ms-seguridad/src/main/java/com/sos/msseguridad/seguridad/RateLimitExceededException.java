package com.sos.msseguridad.seguridad;

/**
 * Se lanza cuando un usuario supera el numero de intentos permitidos.
 * El controlador la traduce a HTTP 429 con la cabecera Retry-After.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String mensaje, long retryAfterSeconds) {
        super(mensaje);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
