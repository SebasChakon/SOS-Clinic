/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
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

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final PersonalRepository personalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TurnoRepository turnoRepository;
    private final PerfilActividadRepository perfilActividadRepository;
    private final CodigoVerificacionRepository codigoVerificacionRepository;
    private final EmailService emailService;
    private final TeleconsultaService teleconsultaService;

    public AuthService(PersonalRepository personalRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, TurnoRepository turnoRepository,
                        PerfilActividadRepository perfilActividadRepository,
                        CodigoVerificacionRepository codigoVerificacionRepository,
                        EmailService emailService, TeleconsultaService teleconsultaService) {
        this.personalRepository = personalRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.turnoRepository = turnoRepository;
        this.perfilActividadRepository = perfilActividadRepository;
        this.codigoVerificacionRepository = codigoVerificacionRepository;
        this.emailService = emailService;
        this.teleconsultaService = teleconsultaService;
    }

    public LoginResponse login(LoginRequest request) {
        Personal personal = validarCredencialesYTurno(request.getUsuario(), request.getClave());

        if (Boolean.TRUE.equals(personal.getRequiere2fa())) {
            enviarCodigo(personal);
            return new LoginResponse(null, personal.getUsuario(), null, null, null, true);
        }

        return construirRespuestaConToken(personal);
    }

    public LoginResponse verificarCodigo(VerificarCodigoRequest request) {
        Personal personal = personalRepository.findByUsuario(request.getUsuario())
                .orElseThrow(() -> new BadCredentialsException("Usuario o codigo incorrectos"));

        CodigoVerificacion codigo = codigoVerificacionRepository
                .findTopByPersonal_IdPersonalAndUsadoFalseOrderByIdCodigoDesc(personal.getIdPersonal())
                .orElseThrow(() -> new BadCredentialsException("No hay un codigo pendiente, intenta iniciar sesion de nuevo"));

        if (codigo.getFechaExpira().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("El codigo expiro, intenta iniciar sesion de nuevo");
        }
        if (!codigo.getCodigo().equals(request.getCodigo().trim())) {
            throw new BadCredentialsException("Codigo incorrecto");
        }

        codigo.setUsado(true);
        codigoVerificacionRepository.save(codigo);

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
        Personal personal = personalRepository.findByUsuario(usuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario o clave incorrectos"));

        if (personal.getIdEstado() == 0) throw new BadCredentialsException("Usuario inactivo");
        if (!passwordEncoder.matches(clave, personal.getClave())) {
            throw new BadCredentialsException("Usuario o clave incorrectos");
        }

        boolean esAdministrador = "administrador".equals(personal.getPerfil().getPerfil());
        if (!esAdministrador && !dentroDeTurno(personal.getIdPersonal())) {
            throw new BadCredentialsException("Fuera de tu horario de turno. Intenta durante tu jornada asignada.");
        }

        return personal;
    }

    private void enviarCodigo(Personal personal) {
        if (personal.getEmail() == null || personal.getEmail().isBlank()) {
            throw new BadCredentialsException(
                    "Tu usuario requiere verificacion por correo (2FA) pero no tiene un correo registrado. Pide al administrador que lo registre.");
        }

        String codigoGenerado = String.format("%06d", (int) (Math.random() * 1_000_000));

        CodigoVerificacion cv = new CodigoVerificacion();
        cv.setPersonal(personal);
        cv.setCodigo(codigoGenerado);
        cv.setFechaExpira(LocalDateTime.now().plusMinutes(5));
        cv.setUsado(false);
        codigoVerificacionRepository.save(cv);

        java.util.Map<String, Object> modelo = new java.util.LinkedHashMap<>();
        modelo.put("nombre", personal.getNombre() + " " + personal.getApellido());
        modelo.put("codigo", codigoGenerado);
        modelo.put("destinatario", personal.getEmail());

        boolean correoEnviado = false;
        try {
            emailService.enviarHtml(personal.getEmail(), "Tu código de verificación · SOS Clinic",
                    emailService.renderizar("plantillas/codigo-verificacion", modelo));
            correoEnviado = true;
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo del código 2FA a {}: {}", personal.getEmail(), e.getMessage());
        }
        log.info("Código 2FA para {} — correo {}: {}",
                personal.getUsuario(),
                correoEnviado ? "enviado a " + personal.getEmail() : "NO enviado (revisar SMTP)",
                correoEnviado ? "(ver bandeja de entrada)" : codigoGenerado);
    }

    private LoginResponse construirRespuestaConToken(Personal personal) {
        String token = jwtService.generarToken(personal.getUsuario(), personal.getIdPersonal(), personal.getPerfil().getPerfil());

        List<ActividadDTO> actividades = perfilActividadRepository
                .findByPerfil_IdPerfil(personal.getPerfil().getIdPerfil())
                .stream()
                .map(pa -> new ActividadDTO(pa.getActividad().getNombreActividad(), pa.getActividad().getEnlace()))
                .collect(Collectors.toList());

        return new LoginResponse(token, personal.getUsuario(), personal.getPerfil().getPerfil(), personal.getIdPersonal(), actividades, false);
    }

    private boolean dentroDeTurno(Integer idPersonal) {
        List<com.sos.msseguridad.modelo.Turno> turnos = turnoRepository.findByPersonal_IdPersonalAndIdEstado(idPersonal, 1);
        LocalTime ahora = LocalTime.now();
        return turnos.stream().anyMatch(turno -> {
            LocalTime inicio = turno.getHoraInicio(), fin = turno.getHoraFin();
            return inicio.isBefore(fin) ? (!ahora.isBefore(inicio) && !ahora.isAfter(fin))
                                         : (!ahora.isBefore(inicio) || !ahora.isAfter(fin));
        });
    }
}