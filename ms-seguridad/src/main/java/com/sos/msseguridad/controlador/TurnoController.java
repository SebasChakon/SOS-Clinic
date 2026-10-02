/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.controlador;

import com.sos.msseguridad.dto.TurnoRequest;
import com.sos.msseguridad.dto.TurnoResponse;
import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.modelo.Turno;
import com.sos.msseguridad.repositorio.PersonalRepository;
import com.sos.msseguridad.repositorio.TurnoRepository;
import com.sos.msseguridad.servicio.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/turnos")
public class TurnoController {

    private final TurnoRepository turnoRepository;
    private final PersonalRepository personalRepository;
    private final AuditoriaService auditoriaService;

    public TurnoController(TurnoRepository turnoRepository, PersonalRepository personalRepository, AuditoriaService auditoriaService) {
        this.turnoRepository = turnoRepository;
        this.personalRepository = personalRepository;
        this.auditoriaService = auditoriaService;
    }

    private TurnoResponse aResponse(Turno t) {
        return new TurnoResponse(t.getIdTurno(), t.getPersonal().getIdPersonal(), t.getPersonal().getUsuario(),
                t.getHoraInicio(), t.getHoraFin(), t.getIdEstado());
    }

    @GetMapping
    public List<TurnoResponse> listar() {
        return turnoRepository.findAll().stream().map(this::aResponse).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody TurnoRequest request) {
        Personal personal = personalRepository.findById(request.getIdPersonal()).orElse(null);
        if (personal == null) return ResponseEntity.badRequest().body("Usuario no encontrado");

        Turno turno = new Turno();
        turno.setPersonal(personal);
        turno.setHoraInicio(request.getHoraInicio());
        turno.setHoraFin(request.getHoraFin());
        turno.setIdEstado(1);
        Turno guardado = turnoRepository.save(turno);

        auditoriaService.registrarAccionActual("CREAR", "Turno: " + personal.getUsuario() + " " + request.getHoraInicio() + "-" + request.getHoraFin());
        return ResponseEntity.ok(aResponse(guardado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @Valid @RequestBody TurnoRequest request) {
        return turnoRepository.findById(id).map(turno -> {
            turno.setHoraInicio(request.getHoraInicio());
            turno.setHoraFin(request.getHoraFin());
            Turno guardado = turnoRepository.save(turno);
            auditoriaService.registrarAccionActual("ACTUALIZAR", "Turno de " + turno.getPersonal().getUsuario());
            return ResponseEntity.ok(aResponse(guardado));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstado(@PathVariable Integer id, @RequestParam Integer idEstado) {
        return turnoRepository.findById(id).map(turno -> {
            turno.setIdEstado(idEstado);
            Turno guardado = turnoRepository.save(turno);
            auditoriaService.registrarAccionActual(idEstado == 1 ? "ACTIVAR" : "DESACTIVAR", "Turno de " + turno.getPersonal().getUsuario());
            return ResponseEntity.ok(aResponse(guardado));
        }).orElse(ResponseEntity.notFound().build());
    }
}