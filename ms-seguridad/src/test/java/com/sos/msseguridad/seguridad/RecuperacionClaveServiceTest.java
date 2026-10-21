package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.modelo.TokenRecuperacion;
import com.sos.msseguridad.repositorio.TokenRecuperacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecuperacionClaveServiceTest {

    private TokenRecuperacionRepository repositorio;
    private RecuperacionClaveService servicio;
    private Personal personal;

    @BeforeEach
    void preparar() {
        repositorio = mock(TokenRecuperacionRepository.class);
        servicio = new RecuperacionClaveService(repositorio);
        ReflectionTestUtils.setField(servicio, "frontendUrl", "http://localhost:5500/");
        personal = new Personal();
        personal.setIdPersonal(7);
        personal.setNombre("Ana");
        personal.setApellido("Perez");
        personal.setEmail("ana@example.test");
    }

    @Test
    void generaTokenOpacoConDuracionDeRecuperacion() {
        when(repositorio.save(any(TokenRecuperacion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        RecuperacionClaveService.TokenGenerado generado = servicio.generar(
                personal, RecuperacionClaveService.TIPO_RECUPERACION, "127.0.0.1", "JUnit");

        assertNotNull(generado.token());
        assertFalse(generado.token().contains("="));
        assertTrue(generado.token().matches("[A-Za-z0-9_-]{40,50}"));
        assertEquals(RecuperacionClaveService.TIPO_RECUPERACION, generado.tipo());
        long minutos = Duration.between(LocalDateTime.now(), generado.expira()).toMinutes();
        assertTrue(minutos >= 29 && minutos <= 30, "Debe durar 30 minutos");

        ArgumentCaptor<TokenRecuperacion> captor = ArgumentCaptor.forClass(TokenRecuperacion.class);
        verify(repositorio).save(captor.capture());
        TokenRecuperacion guardado = captor.getValue();
        assertEquals(RecuperacionClaveService.hash(generado.token()), guardado.getTokenHash());
        assertEquals(64, guardado.getTokenHash().length());
        assertFalse(guardado.getUsado());
        assertEquals("127.0.0.1", guardado.getIpSolicitud());
        verify(repositorio).invalidarPendientes(7, RecuperacionClaveService.TIPO_RECUPERACION);
    }

    @Test
    void generaActivacionConDuracionDeSetentaYDosHoras() {
        RecuperacionClaveService.TokenGenerado generado = servicio.generar(
                personal, RecuperacionClaveService.TIPO_ACTIVACION, null, null);
        long horas = Duration.between(LocalDateTime.now(), generado.expira()).toHours();
        assertTrue(horas >= 71 && horas <= 72, "Debe durar 72 horas");
    }

    @Test
    void validaUnTokenVigente() {
        String raw = "token-de-prueba";
        TokenRecuperacion registro = registro(raw, RecuperacionClaveService.TIPO_RECUPERACION,
                LocalDateTime.now().plusMinutes(10), false);
        when(repositorio.findByTokenHashAndUsadoFalse(RecuperacionClaveService.hash(raw)))
                .thenReturn(Optional.of(registro));

        TokenRecuperacion validado = servicio.validar(raw, RecuperacionClaveService.TIPO_RECUPERACION);
        assertEquals(registro, validado);
    }

    @Test
    void rechazaTokenVacioExpiradoDeOtroTipoOUsado() {
        assertThrows(ResponseStatusException.class,
                () -> servicio.validar(" ", RecuperacionClaveService.TIPO_RECUPERACION));

        String raw = "token-expirado";
        TokenRecuperacion expirado = registro(raw, RecuperacionClaveService.TIPO_RECUPERACION,
                LocalDateTime.now().minusMinutes(1), false);
        when(repositorio.findByTokenHashAndUsadoFalse(RecuperacionClaveService.hash(raw)))
                .thenReturn(Optional.of(expirado));
        assertThrows(ResponseStatusException.class,
                () -> servicio.validar(raw, RecuperacionClaveService.TIPO_RECUPERACION));

        String rawTipo = "token-tipo";
        TokenRecuperacion otroTipo = registro(rawTipo, RecuperacionClaveService.TIPO_ACTIVACION,
                LocalDateTime.now().plusMinutes(10), false);
        when(repositorio.findByTokenHashAndUsadoFalse(RecuperacionClaveService.hash(rawTipo)))
                .thenReturn(Optional.of(otroTipo));
        assertThrows(ResponseStatusException.class,
                () -> servicio.validar(rawTipo, RecuperacionClaveService.TIPO_RECUPERACION));

        when(repositorio.findByTokenHashAndUsadoFalse(anyString())).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> servicio.validar("no-existe", null));
    }

    @Test
    void construyeEnlaceDelFrontendSinDobleBarra() {
        String enlace = servicio.enlace("abc_123");
        assertEquals("http://localhost:5500/restablecer-clave.html?token=abc_123", enlace);
    }

    @Test
    void hashEsDeterministicoYDeSesentaYCuatroCaracteres() {
        String hash = RecuperacionClaveService.hash("abc");
        assertEquals(64, hash.length());
        assertEquals(hash, RecuperacionClaveService.hash("abc"));
        assertFalse(hash.equals(RecuperacionClaveService.hash("abd")));
    }

    @Test
    void marcarUsadoEInvalidarTodosGuardanElEstado() {
        TokenRecuperacion registro = registro("abc", RecuperacionClaveService.TIPO_RECUPERACION,
                LocalDateTime.now().plusMinutes(10), false);
        servicio.marcarUsado(registro);
        assertTrue(registro.getUsado());
        verify(repositorio).save(registro);

        servicio.invalidarTodos(personal);
        verify(repositorio, times(3)).invalidarPendientes(anyInt(), anyString());
    }

    private TokenRecuperacion registro(String raw, String tipo, LocalDateTime expira, boolean usado) {
        TokenRecuperacion registro = new TokenRecuperacion();
        registro.setPersonal(personal);
        registro.setTipo(tipo);
        registro.setTokenHash(RecuperacionClaveService.hash(raw));
        registro.setFechaExpira(expira);
        registro.setUsado(usado);
        return registro;
    }
}
