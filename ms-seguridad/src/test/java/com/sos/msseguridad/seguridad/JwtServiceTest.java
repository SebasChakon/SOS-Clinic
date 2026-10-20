package com.sos.msseguridad.seguridad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtServiceTest {

    private JwtService servicio;

    @BeforeEach
    void preparar() {
        servicio = new JwtService();
        ReflectionTestUtils.setField(servicio, "jwtSecret", "x".repeat(64));
        ReflectionTestUtils.setField(servicio, "jwtExpirationMs", 3_600_000L);
        servicio.validarSecreto();
    }

    @Test
    void normalizaLosPerfilesHistoricos() {
        assertEquals("ADMINISTRADOR", JwtService.rolDePerfil("administrador"));
        assertEquals("COORDINACION", JwtService.rolDePerfil("coordinador_turnos"));
        assertEquals("COORDINACION", JwtService.rolDePerfil("coordinacion"));
        assertEquals("MEDICO", JwtService.rolDePerfil("doctor_triage"));
        assertEquals("MEDICO", JwtService.rolDePerfil("medico"));
        assertEquals("TRIAGE", JwtService.rolDePerfil("triage"));
        assertEquals("SEGURIDAD", JwtService.rolDePerfil("seguridad"));
    }

    @Test
    void conservaIdPersonalYActividadesEnElToken() {
        String token = servicio.generarToken("tester", 7, "doctor_triage",
                List.of("pacientes.html", "lista-espera.html"));

        assertNotNull(token);
        assertEquals("tester", servicio.extraerUsuario(token));
        assertEquals(7, servicio.extraerIdPersonal(token));
        assertEquals("doctor_triage", servicio.extraerPerfil(token));
        assertEquals(List.of("pacientes.html", "lista-espera.html"),
                servicio.extraerActividades(token));
    }
}
