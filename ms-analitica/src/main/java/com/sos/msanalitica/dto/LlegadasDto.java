package com.sos.msanalitica.dto;

import java.util.List;

public record LlegadasDto(
        List<HoraDto> porHora,
        List<ConteoDto> porPrioridad,
        List<ConteoDto> porLocalidad) {
}
