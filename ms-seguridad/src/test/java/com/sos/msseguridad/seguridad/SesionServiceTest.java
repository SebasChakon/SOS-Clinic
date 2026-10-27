package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.modelo.Perfil;
import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.modelo.Sesion;
import com.sos.msseguridad.repositorio.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SesionServiceTest {

    private SesionRepository sesionRepository;
    private SesionService servicio;
    private Personal personal;

    @BeforeEach
    void preparar() {
        sesionRepository = mock(SesionRepository.class);
        servicio = new SesionService(sesionRepository);
        ReflectionTestUtils.setField(servicio, "idleMs", 8 * 60 * 60 * 1000L);
        ReflectionTestUtils.setField(servicio, "maxMs", 12 * 60 * 60 * 1000L);
        servicio.validarDuraciones();

        Perfil perfil = new Perfil();
        perfil.setIdPerfil(2);
        perfil.setPerfil("medico");

        personal = new Personal();
        personal.setIdPersonal(7);
        personal.setUsuario("ana");
        personal.setPerfil(perfil);
        personal.setIdEstado(1);
        personal.setEstadoAprobacion("APROBADA");
    }

    @Test
    void crearGuardaSoloElHashYDevuelveElTokenEnClaro() {
        when(sesionRepository.save(any(Sesion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        SesionService.SesionCreada creada = servicio.crear(personal, "127.0.0.1", "JUnit");

        assertNotNull(creada.token());
        assertFalse(creada.token().isBlank());

        ArgumentCaptor<Sesion> captor = ArgumentCaptor.forClass(Sesion.class);
        verify(sesionRepository).save(captor.capture());
        Sesion guardada = captor.getValue();

        assertNotEquals(creada.token(), guardada.getTokenHash());
        assertEquals(SesionService.hash(creada.token()), guardada.getTokenHash());
        assertEquals(personal, guardada.getPersonal());
        assertFalse(guardada.getRevocada());
        assertTrue(guardada.getFechaExpira().isAfter(LocalDateTime.now()));
        assertTrue(guardada.getFechaMaxima().isAfter(guardada.getFechaExpira()));
    }

    @Test
    void renovarRotaElTokenYExtiendeLaInactividad() {
        Sesion sesion = sesionVigente("token-viejo");
        when(sesionRepository.findByTokenHashAndRevocadaFalse(SesionService.hash("token-viejo")))
                .thenReturn(Optional.of(sesion));
        when(sesionRepository.save(any(Sesion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        SesionService.SesionRenovada renovada = servicio.renovar("token-viejo");

        assertNotEquals("token-viejo", renovada.token());
        assertEquals(SesionService.hash(renovada.token()), sesion.getTokenHash());
        assertEquals(personal, renovada.personal());
        assertTrue(sesion.getFechaExpira().isAfter(LocalDateTime.now()));
    }

    @Test
    void renovarNoSuperaElTopeAbsoluto() {
        Sesion sesion = sesionVigente("token-tope");
        LocalDateTime maximo = LocalDateTime.now().plusMinutes(30);
        sesion.setFechaMaxima(maximo);
        when(sesionRepository.findByTokenHashAndRevocadaFalse(SesionService.hash("token-tope")))
                .thenReturn(Optional.of(sesion));
        when(sesionRepository.save(any(Sesion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        SesionService.SesionRenovada renovada = servicio.renovar("token-tope");

        assertEquals(maximo, renovada.fechaExpira());
        assertEquals(maximo, sesion.getFechaExpira());
    }

    @Test
    void renovarRechazaUnaSesionVencidaYLaRevoca() {
        Sesion sesion = sesionVigente("token-vencido");
        sesion.setFechaExpira(LocalDateTime.now().minusMinutes(1));
        when(sesionRepository.findByTokenHashAndRevocadaFalse(SesionService.hash("token-vencido")))
                .thenReturn(Optional.of(sesion));

        assertThrows(BadCredentialsException.class,
                () -> servicio.renovar("token-vencido"));

        assertTrue(sesion.getRevocada());
        assertEquals("EXPIRADA", sesion.getMotivoRevocacion());
        verify(sesionRepository).save(sesion);
    }

    @Test
    void renovarRechazaUnTokenDesconocido() {
        when(sesionRepository.findByTokenHashAndRevocadaFalse(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> servicio.renovar("no-existe"));
    }

    @Test
    void revocarTodasDelegaEnElRepositorio() {
        when(sesionRepository.revocarTodas(7, "CAMBIO_CLAVE")).thenReturn(2);

        assertEquals(2, servicio.revocarTodas(personal, "CAMBIO_CLAVE"));
        verify(sesionRepository).revocarTodas(7, "CAMBIO_CLAVE");
    }

    private Sesion sesionVigente(String token) {
        Sesion sesion = new Sesion();
        sesion.setPersonal(personal);
        sesion.setTokenHash(SesionService.hash(token));
        sesion.setFechaCreacion(LocalDateTime.now().minusMinutes(10));
        sesion.setUltimoUso(LocalDateTime.now().minusMinutes(10));
        sesion.setFechaExpira(LocalDateTime.now().plusHours(1));
        sesion.setFechaMaxima(LocalDateTime.now().plusHours(12));
        sesion.setRevocada(false);
        return sesion;
    }
}
