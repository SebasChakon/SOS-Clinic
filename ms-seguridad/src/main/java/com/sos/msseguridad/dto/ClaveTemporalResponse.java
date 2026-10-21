package com.sos.msseguridad.dto;

import java.time.LocalDateTime;

public record ClaveTemporalResponse(String claveTemporal, LocalDateTime expira) {
}
