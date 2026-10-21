package com.sos.msseguridad.seguridad;

import java.util.List;

/** Error de politica de claves con el detalle de cada regla incumplida. */
public class PoliticaClaveException extends RuntimeException {

    private final List<String> errores;

    public PoliticaClaveException(List<String> errores) {
        super(String.join(" ", errores));
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}
