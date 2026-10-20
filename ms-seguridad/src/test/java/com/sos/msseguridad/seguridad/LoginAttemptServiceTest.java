package com.sos.msseguridad.seguridad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    @Test
    void bloqueaTrasCincoFallosYSeLiberaConExito() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 4; i++) {
            servicio.registrarFallo("tester");
            assertFalse(servicio.estaBloqueado("tester"));
        }

        servicio.registrarFallo("tester");
        assertTrue(servicio.estaBloqueado("tester"));
        assertTrue(servicio.segundosRestantes("tester") > 0);

        servicio.registrarExito("tester");
        assertFalse(servicio.estaBloqueado("tester"));
    }

    @Test
    void limitaLosReenviosDeCodigo() {
        LoginAttemptService servicio = new LoginAttemptService();

        assertTrue(servicio.puedeReenviar("tester"));
        servicio.registrarReenvio("tester");
        servicio.registrarReenvio("tester");
        servicio.registrarReenvio("tester");

        assertFalse(servicio.puedeReenviar("tester"));
        assertTrue(servicio.segundosParaReenvio("tester") > 0);
    }
}
