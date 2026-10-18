/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mspacientes.dto;

public class EnviarHistoriaRequest {
    /** Texto armado por el frontend antiguo; se ignora si llega idRegistro. */
    private String contenido;

    /** Episodio de triage: si llega, el backend genera el PDF oficial. */
    private Integer idRegistro;

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public Integer getIdRegistro() { return idRegistro; }
    public void setIdRegistro(Integer idRegistro) { this.idRegistro = idRegistro; }
}
