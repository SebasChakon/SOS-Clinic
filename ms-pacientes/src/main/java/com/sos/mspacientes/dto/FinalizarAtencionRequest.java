package com.sos.mspacientes.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Solicitud única de alta de un episodio: el backend se encarga de
 * marcar el alta en ms-triage y de enviar todos los correos.
 */
public class FinalizarAtencionRequest {

    @NotBlank(message = "El diagnóstico final es obligatorio")
    private String diagnosticoFinal;

    @NotBlank(message = "El tratamiento es obligatorio")
    private String tratamiento;

    /**
     * Direccion del paciente escrita a mano. Con ella se geocodifica el
     * punto y se buscan farmacias cercanas para el correo; si falta o no
     * se puede ubicar, el correo incluye farmacias de Bogota en general.
     */
    private String direccion;

    public String getDiagnosticoFinal() { return diagnosticoFinal; }
    public void setDiagnosticoFinal(String diagnosticoFinal) { this.diagnosticoFinal = diagnosticoFinal; }

    public String getTratamiento() { return tratamiento; }
    public void setTratamiento(String tratamiento) { this.tratamiento = tratamiento; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
}
