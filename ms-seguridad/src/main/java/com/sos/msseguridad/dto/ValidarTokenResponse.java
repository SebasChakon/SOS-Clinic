package com.sos.msseguridad.dto;

public record ValidarTokenResponse(boolean valido, String tipo, String usuario) {
}
