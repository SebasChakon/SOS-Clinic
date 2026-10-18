package com.sos.mspacientes.servicio;

/** No fue posible generar el archivo PDF de la historia clinica. */
public class PdfNoGeneradoException extends RuntimeException {

    public PdfNoGeneradoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
