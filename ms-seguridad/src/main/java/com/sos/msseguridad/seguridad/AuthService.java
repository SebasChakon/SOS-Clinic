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

    private final PersonalRepository personalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TurnoRepository turnoRepository;
    private final PerfilActividadRepository perfilActividadRepository;
    private final CodigoVerificacionRepository codigoVerificacionRepository;
    private final EmailService emailService;

    public AuthService(PersonalRepository personalRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, TurnoRepository turnoRepository,
                        PerfilActividadRepository perfilActividadRepository,
                        CodigoVerificacionRepository codigoVerificacionRepository,
                        EmailService emailService) {
        this.personalRepository = personalRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.turnoRepository = turnoRepository;
        this.perfilActividadRepository = perfilActividadRepository;
        this.codigoVerificacionRepository = codigoVerificacionRepository;
        this.emailService = emailService;
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

        LocalDate fechaCita = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(7);
        String fechaHora = fechaCita.atTime(10, 0)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm", Locale.forLanguageTag("es-CO")))
            + " (hora Colombia)";
        String documento = personal.getCedula().replaceAll("[^A-Za-z0-9_-]", "");
        String enlace = "https://meet.jit.si/" + documento + "SOS";
        String cuerpo = "Se confirmó la cita de teleconsulta para el paciente " + nombrePaciente + ".\n\n"
            + "Fecha y hora: " + fechaHora + "\n"
            + "Enlace: " + enlace + "\n";

        emailService.enviar(personal.getEmail(), "Confirmación de teleconsulta SOS", cuerpo);
        return new TeleconsultaResponse(fechaHora, enlace);
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
        String codigoGenerado = String.format("%06d", (int) (Math.random() * 1_000_000));

        CodigoVerificacion cv = new CodigoVerificacion();
        cv.setPersonal(personal);
        cv.setCodigo(codigoGenerado);
        cv.setFechaExpira(LocalDateTime.now().plusMinutes(5));
        cv.setUsado(false);
        codigoVerificacionRepository.save(cv);

        emailService.enviar(personal.getEmail(), "Codigo de verificacion SOS",
                "Tu codigo de acceso es: " + codigoGenerado + "\nExpira en 5 minutos.");
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