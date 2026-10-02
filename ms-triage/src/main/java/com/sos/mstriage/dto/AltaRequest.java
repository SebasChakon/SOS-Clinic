/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mstriage.dto;

import jakarta.validation.constraints.NotBlank;

public class AltaRequest {
    @NotBlank(message = "El diagnostico final es obligatorio")
    private String diagnosticoFinal;
    @NotBlank(message = "El tratamiento/instrucciones es obligatorio")
    private String tratamiento;

    public String getDiagnosticoFinal() { return diagnosticoFinal; }
    public void setDiagnosticoFinal(String diagnosticoFinal) { this.diagnosticoFinal = diagnosticoFinal; }
    public String getTratamiento() { return tratamiento; }
    public void setTratamiento(String tratamiento) { this.tratamiento = tratamiento; }
}
