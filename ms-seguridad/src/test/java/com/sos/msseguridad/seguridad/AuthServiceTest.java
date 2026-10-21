package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.dto.CambiarClaveRequest;
import com.sos.msseguridad.dto.LoginRequest;
import com.sos.msseguridad.dto.LoginResponse;
import com.sos.msseguridad.dto.RecuperarClaveRequest;
import com.sos.msseguridad.dto.VerificarCodigoRequest;
import com.sos.msseguridad.modelo.CodigoVerificacion;
import com.sos.msseguridad.modelo.Perfil;
import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.modelo.Turno;
import com.sos.msseguridad.repositorio.CodigoVerificacionRepository;
import com.sos.msseguridad.repositorio.PerfilActividadRepository;
import com.sos.msseguridad.repositorio.PersonalRepository;
import com.sos.msseguridad.repositorio.TurnoRepository;
import com.sos.msseguridad.servicio.AuditoriaService;
import com.sos.msseguridad.servicio.EmailService;
import com.sos.msseguridad.servicio.TeleconsultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private PersonalRepository personalRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private TurnoRepository turnoRepository;
    private PerfilActividadRepository perfilActividadRepository;
    private CodigoVerificacionRepository codigoVerificacionRepository;
    private RecuperacionClaveService recuperacionClaveService;
    private EmailService emailService;
    private LoginAttemptService loginAttemptService;
    private AuditoriaService auditoriaService;
    private AuthService authService;
    private Personal personal;

    @BeforeEach
    void preparar() {
        personalRepository = mock(PersonalRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        turnoRepository = mock(TurnoRepository.class);
        perfilActividadRepository = mock(PerfilActividadRepository.class);
        codigoVerificacionRepository = mock(CodigoVerificacionRepository.class);
        recuperacionClaveService = mock(RecuperacionClaveService.class);
        emailService = mock(EmailService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        auditoriaService = mock(AuditoriaService.class);
        TeleconsultaService teleconsultaService = mock(TeleconsultaService.class);

        when(passwordEncoder.encode(anyString())).thenReturn("$2b$dummy");
        when(emailService.renderizar(anyString(), anyMap())).thenReturn("<html></html>");
        when(jwtService.generarToken(anyString(), anyInt(), anyString(), anyList(), anyBoolean()))
                .thenReturn("token");
        when(perfilActividadRepository.findByPerfil_IdPerfilAndIdEstado(anyInt(), anyInt()))
                .thenReturn(List.of());

        authService = new AuthService(personalRepository, passwordEncoder, jwtService,
                turnoRepository, perfilActividadRepository, codigoVerificacionRepository,
                recuperacionClaveService, new PoliticaClaves(), emailService,
                teleconsultaService, loginAttemptService, auditoriaService);

        Perfil perfil = new Perfil();
        perfil.setIdPerfil(2);
        perfil.setPerfil("medico");
        perfil.setIdEstado(1);

        personal = new Personal();
        personal.setIdPersonal(7);
        personal.setNombre("Ana");
        personal.setApellido("Perez");
        personal.setEmail("ana@example.test");
        personal.setCedula("10203040");
        personal.setTelefono("3001234567");
        personal.setUsuario("ana");
        personal.setClave("hash-clave");
        personal.setPerfil(perfil);
        personal.setIdEstado(1);
        personal.setEstadoAprobacion("APROBADA");
        personal.setRequiere2fa(true);
        personal.setDebeCambiarClave(false);
    }

    private Turno turnoQueCubreAhora() {
        Turno turno = new Turno();
        turno.setPersonal(personal);
        turno.setHoraInicio(LocalTime.MIN);
        turno.setHoraFin(LocalTime.MAX);
        turno.setIdEstado(1);
        return turno;
    }

    private LoginRequest login(String clave) {
        LoginRequest request = new LoginRequest();
        request.setUsuario("ana");
        request.setClave(clave);
        return request;
    }

    @Test
    void loginCon2faDevuelveChallengeSinToken() {
        when(personalRepository.findByUsuarioIgnoreCase("ana")).thenReturn(Optional.of(personal));
        when(passwordEncoder.matches("Cobre#7391", "hash-clave")).thenReturn(true);
        when(turnoRepository.findByPersonal_IdPersonalAndIdEstado(7, 1))
                .thenReturn(List.of(turnoQueCubreAhora()));
        when(loginAttemptService.estaBloqueado("ana")).thenReturn(false);
        when(loginAttemptService.puedeReenviar("ana")).thenReturn(true);

        LoginResponse respuesta = authService.login(login("Cobre#7391"));

        assertTrue(respuesta.getRequiere2fa());
        assertNotNull(respuesta.getChallenge());
        assertNull(respuesta.getToken());
        assertFalse(respuesta.getDebeCambiarClave());
        verify(codigoVerificacionRepository).save(any(CodigoVerificacion.class));
        verify(emailService).enviarHtml(eq("ana@example.test"), anyString(), anyString());
    }

    @Test
    void verificarCodigoValidoDevuelveToken() {
        CodigoVerificacion codigo = new CodigoVerificacion();
        codigo.setPersonal(personal);
        codigo.setCodigo("hash-codigo");
        codigo.setChallengeHash(RecuperacionClaveService.hash("challenge"));
        codigo.setProposito("LOGIN");
        codigo.setFechaExpira(LocalDateTime.now().plusMinutes(5));
        codigo.setUsado(false);
        when(codigoVerificacionRepository
                .findTopByChallengeHashAndPropositoAndUsadoFalseOrderByIdCodigoDesc(
                        RecuperacionClaveService.hash("challenge"), "LOGIN"))
                .thenReturn(Optional.of(codigo));
        when(passwordEncoder.matches("123456", "hash-codigo")).thenReturn(true);

        VerificarCodigoRequest request = new VerificarCodigoRequest();
        request.setChallenge("challenge");
        request.setCodigo("123456");

        LoginResponse respuesta = authService.verificarCodigo(request);

        assertEquals("token", respuesta.getToken());
        assertTrue(codigo.getUsado());
        verify(codigoVerificacionRepository).save(codigo);
    }

    @Test
    void verificarCodigoConChallengeInexistenteFallaSinCrearContador() {
        when(codigoVerificacionRepository
                .findTopByChallengeHashAndPropositoAndUsadoFalseOrderByIdCodigoDesc(anyString(), anyString()))
                .thenReturn(Optional.empty());

        VerificarCodigoRequest request = new VerificarCodigoRequest();
        request.setChallenge("no-existe");
        request.setCodigo("123456");

        assertThrows(BadCredentialsException.class, () -> authService.verificarCodigo(request));
        verify(loginAttemptService, never()).registrarFallo(anyString());
    }

    @Test
    void loginConClaveTemporalVencidaFallaConNoAutorizado() {
        personal.setRequiere2fa(false);
        personal.setClaveExpira(LocalDateTime.now().minusMinutes(1));
        when(personalRepository.findByUsuarioIgnoreCase("ana")).thenReturn(Optional.of(personal));
        when(passwordEncoder.matches("Cobre#7391", "hash-clave")).thenReturn(true);
        when(turnoRepository.findByPersonal_IdPersonalAndIdEstado(7, 1))
                .thenReturn(List.of(turnoQueCubreAhora()));
        when(loginAttemptService.estaBloqueado("ana")).thenReturn(false);

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
                () -> authService.login(login("Cobre#7391")));
        assertEquals(HttpStatus.UNAUTHORIZED, excepcion.getStatusCode());
    }

    @Test
    void loginDeCuentaPendienteFallaGenericoYCuentaElIntento() {
        personal.setIdEstado(0);
        personal.setEstadoAprobacion("PENDIENTE");
        personal.setRequiere2fa(false);
        when(personalRepository.findByUsuarioIgnoreCase("ana"))
                .thenReturn(Optional.of(personal));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(loginAttemptService.estaBloqueado("ana")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(login("Cobre#7391")));
        verify(loginAttemptService).registrarFallo("ana");
    }

    @Test
    void cambiarClaveValidaPoliticaYDevuelveTokenNuevo() {
        personal.setDebeCambiarClave(true);
        personal.setClaveExpira(LocalDateTime.now().plusHours(1));
        when(personalRepository.findByUsuarioIgnoreCase("ana")).thenReturn(Optional.of(personal));
        when(passwordEncoder.matches("Cobre#7391", "hash-clave")).thenReturn(true);
        when(loginAttemptService.estaBloqueado("cambio:ana")).thenReturn(false);

        CambiarClaveRequest request = new CambiarClaveRequest();
        request.setClaveActual("Cobre#7391");
        request.setClaveNueva("Nube#4821");

        LoginResponse respuesta = authService.cambiarClave("ana", request);

        assertEquals("token", respuesta.getToken());
        assertFalse(personal.getDebeCambiarClave());
        assertNull(personal.getClaveExpira());
        assertNotNull(personal.getFechaCambioClave());
    }

    @Test
    void cambiarClaveConPoliticaInvalidaNoModificaLaCuenta() {
        personal.setDebeCambiarClave(true);
        personal.setClaveExpira(LocalDateTime.now().plusHours(1));
        when(personalRepository.findByUsuarioIgnoreCase("ana")).thenReturn(Optional.of(personal));
        when(passwordEncoder.matches("Cobre#7391", "hash-clave")).thenReturn(true);
        when(loginAttemptService.estaBloqueado("cambio:ana")).thenReturn(false);

        CambiarClaveRequest request = new CambiarClaveRequest();
        request.setClaveActual("Cobre#7391");
        request.setClaveNueva("Password1!");

        assertThrows(PoliticaClaveException.class, () -> authService.cambiarClave("ana", request));
        assertTrue(personal.getDebeCambiarClave());
        assertNotNull(personal.getClaveExpira());
    }

    @Test
    void recuperarClaveDeUsuarioInexistenteNoLanzaNiEnviaCorreo() {
        when(personalRepository.findByUsuarioIgnoreCase("nadie")).thenReturn(Optional.empty());
        when(personalRepository.findByEmailIgnoreCase("nadie")).thenReturn(Optional.empty());

        RecuperarClaveRequest request = new RecuperarClaveRequest();
        request.setIdentificador("nadie");

        authService.recuperarClave(request, "127.0.0.1", "JUnit");

        verify(emailService, never()).enviarHtml(anyString(), anyString(), anyString());
    }
}
