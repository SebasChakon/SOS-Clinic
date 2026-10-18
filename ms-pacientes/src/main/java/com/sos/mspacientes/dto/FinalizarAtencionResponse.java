package com.sos.mspacientes.dto;

import java.util.List;

/**
 * Resultado del alta: siempre informa primero si la atencion quedo
 * guardada (altaOk) y luego el estado individual de cada correo,
 * para que la pantalla pueda ofrecer reintento cuando algo fallo.
 */
public record FinalizarAtencionResponse(
        boolean altaOk,
        List<ResultadoEnvioCorreo> correos,
        String citaFechaHora,
        String citaEnlace) {

    public boolean hayCorreosFallidos() {
        return correos != null && correos.stream()
                .anyMatch(c -> ResultadoEnvioCorreo.FALLIDO.equals(c.estado()));
    }
}
