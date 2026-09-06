/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mspacientes.controlador;

import com.sos.mspacientes.dto.PacienteRequest;
import com.sos.mspacientes.modelo.EstadoPaciente;
import com.sos.mspacientes.modelo.Paciente;
import com.sos.mspacientes.repositorio.EstadoPacienteRepository;
import com.sos.mspacientes.repositorio.PacienteRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pacientes")
public class PacienteController {

    private final PacienteRepository pacienteRepository;
    private final EstadoPacienteRepository estadoPacienteRepository;

    public PacienteController(PacienteRepository pacienteRepository,
                               EstadoPacienteRepository estadoPacienteRepository) {
        this.pacienteRepository = pacienteRepository;
        this.estadoPacienteRepository = estadoPacienteRepository;
    }

    @PostMapping
    public ResponseEntity<?> registrar(@Valid @RequestBody PacienteRequest request) {
        if (pacienteRepository.existsByCedula(request.getCedula())) {
            return ResponseEntity.status(409).body("Ya existe un paciente con esa cedula");
        }

        Paciente paciente = new Paciente();
        paciente.setNombre(request.getNombre());
        paciente.setApellido(request.getApellido());
        paciente.setCedula(request.getCedula());
        paciente.setFechaNacimiento(request.getFechaNacimiento());
        paciente.setIdEstado(1);

        return ResponseEntity.ok(pacienteRepository.save(paciente));
    }

    @GetMapping
    public List<Paciente> listar() {
        return pacienteRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Integer id) {
        return pacienteRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cedula/{cedula}")
    public ResponseEntity<?> obtenerPorCedula(@PathVariable String cedula) {
        return pacienteRepository.findByCedula(cedula)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/signos-vitales")
    public ResponseEntity<?> registrarSignosVitales(@RequestBody EstadoPaciente estadoPaciente) {
        estadoPaciente.setFechaHoraToma(java.time.LocalDateTime.now());
        estadoPaciente.setIdEstado(1);
        return ResponseEntity.ok(estadoPacienteRepository.save(estadoPaciente));
    }

    @GetMapping("/signos-vitales/registro/{idRegistro}")
    public List<EstadoPaciente> obtenerSignosVitalesPorRegistro(@PathVariable Integer idRegistro) {
        return estadoPacienteRepository.findByIdRegistro(idRegistro);
    }
}
