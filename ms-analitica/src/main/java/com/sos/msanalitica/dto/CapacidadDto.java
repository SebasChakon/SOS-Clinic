package com.sos.msanalitica.dto;

public record CapacidadDto(
        String turno,
        double llegadasPromedio,
        double personalPromedio,
        double puestosTriagePromedio,
        double camasDisponiblesPromedio) {
}
