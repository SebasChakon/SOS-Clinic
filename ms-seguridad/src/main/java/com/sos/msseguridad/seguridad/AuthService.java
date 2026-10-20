package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.dto.ActividadDTO;
import com.sos.msseguridad.dto.LoginRequest;
import com.sos.msseguridad.dto.LoginResponse;
import com.sos.msseguridad.dto.TeleconsultaResponse;
import com.sos.msseguridad.dto.VerificarCodigoRequest;
import com.sos.msseguridad.modelo.CodigoVerificacion;
import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.repositorio.CodigoVerificacionRepository;
import com.sos.msseguridad.repositorio.PerfilActividadRepository;
import com.sos.msseguridad.repositorio.PersonalRepository;
import com.sos.msseguridad.repositorio.TurnoRepository;
import com.sos.msseguridad.servicio.EmailService;
import com.sos.msseguridad.servicio.TeleconsultaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String MENSAJE_CREDENCIALES = "Usuario o clave incorrectos";
    private static final String MENSAJE_CODIGO = "Codigo incorrecto o expirado";

    private final PersonalRepository personalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TurnoRepository turnoRepository;
    private final PerfilActividadRepository perfilActividadRepository;
    private final CodigoVerificacionRepository codigoVerificacionRepository;
    private final EmailService emailService;
    private final TeleconsultaService teleconsultaService;
    private final LoginAttemptService loginAttemptService;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyHash;

    public AuthService(PersonalRepository personalRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, TurnoRepository turnoRepository,
                       PerfilActividadRepository perfilActividadRepository,
                       CodigoVerificacionRepository codigoVerificacionRepository,
                       EmailService emailService, TeleconsultaService teleconsultaService,
                       LoginAttemptService loginAttemptService) {
        this.personalRepository = personalRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.turnoRepository = turnoRepository;
        this.perfilActividadRepository = perfilActividadRepository;
        this.codigoVerificacionRepository = codigoVerificacionRepository;
        this.emailService = emailService;
        this.teleconsultaService = teleconsultaService;
        this.loginAttemptService = loginAttemptService;
        this.dummyHash = passwordEncoder.encode("hash-dummy-para-igualar-tiempos");
    }

    public LoginResponse login(LoginRequest request) {
        String usuario = request.getUsuario() == null ? "" : request.getUsuario().trim();
        String claveIntentos = normalizarUsuario(usuario);

        if (loginAttemptService.estaBloqueado(claveIntentos)) {
            throw new RateLimitExceededException(
                    "Demasiados intentos fallidos. Intenta de nuevo mas tarde.",
                    loginAttemptService.segundosRestantes(claveIntentos));
        }

        Personal personal;
        try {
            personal = validarCredencialesYTurno(usuario, request.getClave());
        } catch (BadCredentialsException e) {
            loginAttemptService.registrarFallo(claveIntentos);
            throw e;
        }

        loginAttemptService.registrarExito(claveIntentos);

        if (Boolean.TRUE.equals(personal.getRequiere2fa())) {
            enviarCodigo(personal);
            return new LoginResponse(null, personal.getUsuario(), null, null, null, true);
        }

        return construirRespuestaConToken(personal);
    }

    public LoginResponse verificarCodigo(VerificarCodigoRequest request) {
        String usuario = request.getUsuario() == null ? "" : request.getUsuario().trim();
        String claveIntentos = "2fa:" + normalizarUsuario(usuario);

        if (loginAttemptService.estaBloqueado(claveIntentos)) {
            throw new RateLimitExceededException(
                    "Demasiados intentos fallidos. Intenta de nuevo mas tarde.",
                    loginAttemptService.segundosRestantes(claveIntentos));
        }

        Personal personal = personalRepository.findByUsuario(usuario).orElse(null);
        if (personal == null || !Integer.valueOf(1).equals(personal.getIdEstado())
                || !puedeIniciarSesion(personal)) {
            passwordEncoder.matches("000000", dummyHash);
            loginAttemptService.registrarFallo(claveIntentos);
            throw new BadCredentialsException(MENSAJE_CODIGO);
        }

        CodigoVerificacion codigo = codigoVerificacionRepository
                .findTopByPersonal_IdPersonalAndUsadoFalseOrderByIdCodigoDesc(personal.getIdPersonal())
                .orElse(null);

        if (codigo == null || codigo.getFechaExpira().isBefore(LocalDateTime.now())
                || !passwordEncoder.matches(request.getCodigo().trim(), codigo.getCodigo())) {
            registrarFallo2fa(claveIntentos, codigo);
            throw new BadCredentialsException(MENSAJE_CODIGO);
        }

        codigo.setUsado(true);
        codigoVerificacionRepository.save(codigo);
        loginAttemptService.registrarExito(claveIntentos);

        return construirRespuestaConToken(personal);
    }

    public TeleconsultaResponse enviarConfirmacionTeleconsulta(String usuario, String nombrePaciente) {
        Personal personal = personalRepository.findByUsuario(usuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario autenticado no encontrado"));

        if (personal.getEmail() == null || personal.getEmail().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "El usuario que dio de alta no tiene correo registrado");
        }
        if (personal.getCedula() == null || personal.getCedula().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "El usuario que dio de alta no tiene documento registrado");
        }

        TeleconsultaService.CitaTeleconsulta cita = teleconsultaService.generarCita(personal.getCedula());
        teleconsultaService.confirmarPorCorreo(personal, nombrePaciente, cita);

        return new TeleconsultaResponse(cita.fechaHora(), cita.enlace(),
                cita.inicio().toString(), cita.fin().toString());
    }

    private Personal validarCredencialesYTurno(String usuario, String clave) {
        Personal personal = personalRepository.findByUsuario(usuario).orElse(null);

        if (personal == null) {
            passwordEncoder.matches(clave == null ? "" : clave, dummyHash);
            throw new BadCredentialsException(MENSAJE_CREDENCIALES);
        }

        if (!Integer.valueOf(1).equals(personal.getIdEstado())) {
            passwordEncoder.matches(clave == null ? "" : clave, dummyHash);
            throw new BadCredentialsException(MENSAJE_CREDENCIALES);
        }

        if (clave == null || !passwordEncoder.matches(clave, personal.getClave())) {
            throw new BadCredentialsException(MENSAJE_CREDENCIALES);
        }

        if (!puedeIniciarSesion(personal)) {
            throw new BadCredentialsException("No puedes iniciar sesion en este momento.");
        }

        return personal;
    }

    private boolean puedeIniciarSesion(Personal personal) {
        boolean esAdministrador = personal.getPerfil() != null
                && "administrador".equalsIgnoreCase(personal.getPerfil().getPerfil());
        return esAdministrador || dentroDeTurno(personal.getIdPersonal());
    }

    private void enviarCodigo(Personal personal) {
        String usuario = normalizarUsuario(personal.getUsuario());

        if (!loginAttemptService.puedeReenviar(usuario)) {
            throw new RateLimitExceededException(
                    "Demasiados codigos solicitados. Intenta de nuevo mas tarde.",
                    loginAttemptService.segundosParaReenvio(usuario));
        }

        if (personal.getEmail() == null || personal.getEmail().isBlank()) {
            throw new BadCredentialsException(
                    "No se pudo enviar el codigo de verificacion. Contacta al administrador.");
        }

        codigoVerificacionRepository.invalidarPendientes(personal.getIdPersonal());

        String codigoGenerado = String.format("%06d", secureRandom.nextInt(1_000_000));

        CodigoVerificacion cv = new CodigoVerificacion();
        cv.setPersonal(personal);
        cv.setCodigo(passwordEncoder.encode(codigoGenerado));
        cv.setFechaExpira(LocalDateTime.now().plusMinutes(5));
        cv.setUsado(false);
        codigoVerificacionRepository.save(cv);

        java.util.Map<String, Object> modelo = new java.util.LinkedHashMap<>();
        modelo.put("nombre", personal.getNombre() + " " + personal.getApellido());
        modelo.put("codigo", codigoGenerado);
        modelo.put("destinatario", personal.getEmail());

        try {
            emailService.enviarHtml(personal.getEmail(), "Tu codigo de verificacion - SOS Clinic",
                    emailService.renderizar("plantillas/codigo-verificacion", modelo));
        } catch (Exception e) {
            cv.setUsado(true);
            codigoVerificacionRepository.save(cv);
            log.warn("No se pudo enviar el codigo 2FA al usuario {}", usuario);
            throw new BadCredentialsException(
                    "No se pudo enviar el codigo de verificacion. Intenta de nuevo mas tarde.");
        }

        loginAttemptService.registrarReenvio(usuario);
        log.info("Codigo 2FA generado para el usuario {}", usuario);
    }

    private void registrarFallo2fa(String claveIntentos, CodigoVerificacion codigo) {
        loginAttemptService.registrarFallo(claveIntentos);
        if (loginAttemptService.estaBloqueado(claveIntentos) && codigo != null) {
            codigo.setUsado(true);
            codigoVerificacionRepository.save(codigo);
        }
    }

    private LoginResponse construirRespuestaConToken(Personal personal) {
        List<ActividadDTO> actividades = perfilActividadRepository
                .findByPerfil_IdPerfil(personal.getPerfil().getIdPerfil())
                .stream()
                .map(pa -> new ActividadDTO(pa.getActividad().getNombreActividad(), pa.getActividad().getEnlace()))
                .collect(Collectors.toList());

        List<String> enlaces = actividades.stream()
                .map(ActividadDTO::getEnlace)
                .toList();

        String token = jwtService.generarToken(personal.getUsuario(), personal.getIdPersonal(),
                personal.getPerfil().getPerfil(), enlaces);

        return new LoginResponse(token, personal.getUsuario(), personal.getPerfil().getPerfil(),
                personal.getIdPersonal(), actividades, false);
    }

    private boolean dentroDeTurno(Integer idPersonal) {
        List<com.sos.msseguridad.modelo.Turno> turnos =
                turnoRepository.findByPersonal_IdPersonalAndIdEstado(idPersonal, 1);
        LocalTime ahora = LocalTime.now();
        return turnos.stream().anyMatch(turno -> {
            LocalTime inicio = turno.getHoraInicio(), fin = turno.getHoraFin();
            return inicio.isBefore(fin) ? (!ahora.isBefore(inicio) && !ahora.isAfter(fin))
                                         : (!ahora.isBefore(inicio) || !ahora.isAfter(fin));
        });
    }

    private static String normalizarUsuario(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }
}
