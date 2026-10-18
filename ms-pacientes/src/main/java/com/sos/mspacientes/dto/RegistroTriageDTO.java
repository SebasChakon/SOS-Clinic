package com.sos.mspacientes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Vista del episodio de triage tal como la devuelve ms-triage
 * (GET /triage/{id}). Solo se mapean los campos que consume
 * la historia clinica y la orquestacion del alta.
 */
public record RegistroTriageDTO(
        Integer idRegistro,
        Integer idPaciente,
        String ubicacion,
        LocalDateTime fechaHoraLlegada,
        String diagnosticoFinal,
        String tratamiento,
        EstadoTriageDTO estadoTriage,
        CategoriaTriageDTO categoriaTriage) {

    public record EstadoTriageDTO(
            @JsonProperty("idEstadoTriage") Integer id,
            @JsonProperty("nombreEstado") String nombre) {
    }

    public record CategoriaTriageDTO(
            @JsonProperty("idCategoriaTriage") Integer id,
            @JsonProperty("nombreCategoria") String nombre,
            String descripcion,
            Integer prioridad) {
    }

    public boolean estaAtendido() {
        return estadoTriage != null && "Atendido".equalsIgnoreCase(estadoTriage.nombre());
    }
}
