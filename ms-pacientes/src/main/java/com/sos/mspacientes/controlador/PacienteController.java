/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mspacientes.controlador;

import com.sos.mspacientes.dto.EnviarHistoriaRequest;
import com.sos.mspacientes.dto.ConfirmacionTeleconsultaRequest;
import com.sos.mspacientes.dto.PacienteRequest;
import com.sos.mspacientes.modelo.EstadoPaciente;
import com.sos.mspacientes.modelo.Paciente;
import com.sos.mspacientes.repositorio.EstadoPacienteRepository;
import com.sos.mspacientes.repositorio.PacienteRepository;
import com.sos.mspacientes.servicio.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pacientes")
public class PacienteController {

    private final PacienteRepository pacienteRepository;
    private final EstadoPacienteRepository estadoPacienteRepository;
    private final EmailService emailService;

    public PacienteController(PacienteRepository pacienteRepository,
                               EstadoPacienteRepository estadoPacienteRepository,
                               EmailService emailService) {
        this.pacienteRepository = pacienteRepository;
        this.estadoPacienteRepository = estadoPacienteRepository;
        this.emailService = emailService;
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
        paciente.setEmail(request.getEmail());

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

    @PostMapping("/{id}/enviar-historia")
    public ResponseEntity<?> enviarHistoria(@PathVariable Integer id, @RequestBody EnviarHistoriaRequest request) {
        return pacienteRepository.findById(id).map(paciente -> {
            if (paciente.getEmail() == null || paciente.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body("Este paciente no tiene correo registrado");
            }
            emailService.enviar(paciente.getEmail(), "Tu historia clinica - SOS", request.getContenido());
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/enviar-cita-teleconsulta")
    public ResponseEntity<?> enviarCitaTeleconsulta(@PathVariable Integer id,
                                                      @Valid @RequestBody ConfirmacionTeleconsultaRequest request) {
        return pacienteRepository.findById(id).map(paciente -> {
            if (paciente.getEmail() == null || paciente.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body("Este paciente no tiene correo registrado");
            }
            String cuerpo = "Hola " + paciente.getNombre() + " " + paciente.getApellido() + ",\n\n"
                    + "Tu cita de teleconsulta SOS ha sido agendada.\n\n"
                    + "Fecha y hora: " + request.getFechaHora() + "\n"
                    + "Enlace para conectarte: " + request.getEnlace() + "\n";
            emailService.enviar(paciente.getEmail(), "Cita de teleconsulta SOS", cuerpo);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/buscar")
    public List<Paciente> buscar(@RequestParam String q) {
        if (q == null || q.isBlank()) return List.of();
        return pacienteRepository.findTop10ByCedulaContainingOrderByCedula(q.trim());
    }
}