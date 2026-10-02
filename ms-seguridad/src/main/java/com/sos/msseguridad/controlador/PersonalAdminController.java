/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.controlador;

import com.sos.msseguridad.dto.PersonalRequest;
import com.sos.msseguridad.dto.PersonalResponse;
import com.sos.msseguridad.modelo.Perfil;
import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.modelo.Turno;
import com.sos.msseguridad.repositorio.PerfilRepository;
import com.sos.msseguridad.repositorio.PersonalRepository;
import com.sos.msseguridad.repositorio.TurnoRepository;
import com.sos.msseguridad.servicio.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/personal")
public class PersonalAdminController {

    private final PersonalRepository personalRepository;
    private final PerfilRepository perfilRepository;
    private final TurnoRepository turnoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public PersonalAdminController(PersonalRepository personalRepository,
                                    PerfilRepository perfilRepository,
                                    TurnoRepository turnoRepository,
                                    PasswordEncoder passwordEncoder,
                                    AuditoriaService auditoriaService) {
        this.personalRepository = personalRepository;
        this.perfilRepository = perfilRepository;
        this.turnoRepository = turnoRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    private PersonalResponse aResponse(Personal p) {
        return new PersonalResponse(p.getIdPersonal(), p.getNombre(), p.getApellido(), p.getEmail(),
                p.getCedula(), p.getTelefono(), p.getUsuario(), p.getPerfil().getIdPerfil(),
                p.getPerfil().getPerfil(), p.getIdEstado(), p.getRequiere2fa());
    }

    @GetMapping
    public List<PersonalResponse> listar() {
        return personalRepository.findAll().stream().map(this::aResponse).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody PersonalRequest request) {
        if (request.getClave() == null || request.getClave().isBlank()) {
            return ResponseEntity.badRequest().body("La clave es obligatoria al crear un usuario nuevo");
        }
        if (request.getHoraInicio() == null || request.getHoraFin() == null) {
            return ResponseEntity.badRequest().body("El turno (hora de inicio y hora de fin) es obligatorio al crear un usuario");
        }
        Perfil perfil = perfilRepository.findById(request.getIdPerfil()).orElse(null);
        if (perfil == null) return ResponseEntity.badRequest().body("Perfil no encontrado");

        Personal p = new Personal();
        p.setNombre(request.getNombre());
        p.setApellido(request.getApellido());
        p.setEmail(request.getEmail());
        p.setCedula(request.getCedula());
        p.setTelefono(request.getTelefono());
        p.setUsuario(request.getUsuario());
        p.setClave(passwordEncoder.encode(request.getClave()));
        p.setPerfil(perfil);
        p.setIdEstado(1);
        p.setRequiere2fa(request.getRequiere2fa() != null ? request.getRequiere2fa() : false);
        Personal guardado = personalRepository.save(p);

        Turno turno = new Turno();
        turno.setPersonal(guardado);
        turno.setHoraInicio(request.getHoraInicio());
        turno.setHoraFin(request.getHoraFin());
        turno.setIdEstado(1);
        turnoRepository.save(turno);

        auditoriaService.registrarAccionActual("CREAR", "Usuario: " + guardado.getUsuario() + " (" + perfil.getPerfil() + ")");
        return ResponseEntity.ok(aResponse(guardado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @Valid @RequestBody PersonalRequest request) {
        return personalRepository.findById(id).map(p -> {
            Perfil perfil = perfilRepository.findById(request.getIdPerfil()).orElse(null);
            if (perfil == null) return ResponseEntity.badRequest().body("Perfil no encontrado");

            p.setNombre(request.getNombre());
            p.setApellido(request.getApellido());
            p.setEmail(request.getEmail());
            p.setCedula(request.getCedula());
            p.setTelefono(request.getTelefono());
            p.setUsuario(request.getUsuario());
            p.setPerfil(perfil);
            if (request.getRequiere2fa() != null) {
                p.setRequiere2fa(request.getRequiere2fa());
            }

            if (request.getClave() != null && !request.getClave().isBlank()) {
                p.setClave(passwordEncoder.encode(request.getClave()));
            }

            Personal guardado = personalRepository.save(p);

            // Upsert del turno: si ya tiene uno(s) activo(s), actualiza el mas reciente
            // y desactiva cualquier duplicado viejo que haya quedado suelto
            if (request.getHoraInicio() != null && request.getHoraFin() != null) {
                List<Turno> turnosActivos = turnoRepository.findByPersonal_IdPersonalAndIdEstado(guardado.getIdPersonal(), 1);

                if (!turnosActivos.isEmpty()) {
                    Turno turnoMasReciente = turnosActivos.stream()
                            .max((a, b) -> a.getIdTurno().compareTo(b.getIdTurno()))
                            .get();

                    turnoMasReciente.setHoraInicio(request.getHoraInicio());
                    turnoMasReciente.setHoraFin(request.getHoraFin());
                    turnoRepository.save(turnoMasReciente);

                    turnosActivos.stream()
                            .filter(t -> !t.getIdTurno().equals(turnoMasReciente.getIdTurno()))
                            .forEach(t -> { t.setIdEstado(0); turnoRepository.save(t); });
                } else {
                    Turno turno = new Turno();
                    turno.setPersonal(guardado);
                    turno.setHoraInicio(request.getHoraInicio());
                    turno.setHoraFin(request.getHoraFin());
                    turno.setIdEstado(1);
                    turnoRepository.save(turno);
                }
            }

            auditoriaService.registrarAccionActual("ACTUALIZAR", "Usuario: " + guardado.getUsuario());
            return ResponseEntity.ok(aResponse(guardado));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstado(@PathVariable Integer id, @RequestParam Integer idEstado) {
        return personalRepository.findById(id).map(p -> {
            p.setIdEstado(idEstado);
            Personal guardado = personalRepository.save(p);
            auditoriaService.registrarAccionActual(idEstado == 1 ? "ACTIVAR" : "DESACTIVAR", "Usuario: " + guardado.getUsuario());
            return ResponseEntity.ok(aResponse(guardado));
        }).orElse(ResponseEntity.notFound().build());
    }
}