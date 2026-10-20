package com.sos.msanalitica.seguridad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtAuthFilterTest {

    @Test
    void normalizaElPerfilHistoricoDeCoordinacion() {
        assertEquals("COORDINACION", JwtAuthFilter.rolDePerfil("coordinador_turnos"));
        assertEquals("COORDINACION", JwtAuthFilter.rolDePerfil("coordinacion"));
        assertEquals("ADMINISTRADOR", JwtAuthFilter.rolDePerfil("administrador"));
        assertEquals("MEDICO", JwtAuthFilter.rolDePerfil("doctor_triage"));
    }
}
