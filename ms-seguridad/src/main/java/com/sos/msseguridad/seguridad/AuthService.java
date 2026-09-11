/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.dto.ActividadDTO;
import com.sos.msseguridad.dto.LoginRequest;
import com.sos.msseguridad.dto.LoginResponse;
import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.modelo.PerfilActividad;
import com.sos.msseguridad.modelo.Turno;
import com.sos.msseguridad.repositorio.PerfilActividadRepository;
import com.sos.msseguridad.repositorio.PersonalRepository;
import com.sos.msseguridad.repositorio.TurnoRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final PersonalRepository personalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TurnoRepository turnoRepository;
    private final PerfilActividadRepository perfilActividadRepository;

    public AuthService(PersonalRepository personalRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        TurnoRepository turnoRepository,
                        PerfilActividadRepository perfilActividadRepository) {
        this.personalRepository = personalRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.turnoRepository = turnoRepository;
        this.perfilActividadRepository = perfilActividadRepository;
    }

    public LoginResponse login(LoginRequest request) {
        Personal personal = personalRepository.findByUsuario(request.getUsuario())
                .orElseThrow(() -> new BadCredentialsException("Usuario o clave incorrectos"));

        if (personal.getIdEstado() == 0) {
            throw new BadCredentialsException("Usuario inactivo");
        }

        if (!passwordEncoder.matches(request.getClave(), personal.getClave())) {
            throw new BadCredentialsException("Usuario o clave incorrectos");
        }

        // El administrador no esta sujeto a control de turnos
        boolean esAdministrador = "administrador".equals(personal.getPerfil().getPerfil());

        if (!esAdministrador && !dentroDeTurno(personal.getIdPersonal())) {
            throw new BadCredentialsException("Fuera de tu horario de turno. Intenta durante tu jornada asignada.");
        }

        String token = jwtService.generarToken(
                personal.getUsuario(),
                personal.getIdPersonal(),
                personal.getPerfil().getPerfil()
        );

        List<ActividadDTO> actividades = perfilActividadRepository
                .findByPerfil_IdPerfil(personal.getPerfil().getIdPerfil())
                .stream()
                .map(pa -> new ActividadDTO(pa.getActividad().getNombreActividad(), pa.getActividad().getEnlace()))
                .collect(Collectors.toList());

        return new LoginResponse(token, personal.getUsuario(), personal.getPerfil().getPerfil(), personal.getIdPersonal(), actividades);
    }

    private boolean dentroDeTurno(Integer idPersonal) {
        List<Turno> turnos = turnoRepository.findByPersonal_IdPersonalAndIdEstado(idPersonal, 1);
        LocalTime ahora = LocalTime.now();

        return turnos.stream().anyMatch(turno -> {
            LocalTime inicio = turno.getHoraInicio();
            LocalTime fin = turno.getHoraFin();

            if (inicio.isBefore(fin)) {
                // Turno normal, dentro del mismo dia (ej. 08:00 a 16:00)
                return !ahora.isBefore(inicio) && !ahora.isAfter(fin);
            } else {
                // Turno nocturno que cruza medianoche (ej. 22:00 a 06:00)
                return !ahora.isBefore(inicio) || !ahora.isAfter(fin);
            }
        });
    }
}
