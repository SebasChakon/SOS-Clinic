/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mspacientes.controlador;

import com.sos.mspacientes.dto.EnviarHistoriaRequest;
import com.sos.mspacientes.dto.ConfirmacionTeleconsultaRequest;
import com.sos.mspacientes.dto.PacienteRequest;
import com.sos.mspacientes.dto.SignosVitalesRequest;
import com.sos.mspacientes.modelo.EstadoPaciente;
import com.sos.mspacientes.modelo.Paciente;
import com.sos.mspacientes.repositorio.EstadoPacienteRepository;
import com.sos.mspacientes.repositorio.PacienteRepository;
import com.sos.mspacientes.seguridad.UsuarioJwt;
import com.sos.mspacientes.servicio.EmailNoEnviadoException;
import com.sos.mspacientes.servicio.EmailService;
import com.sos.mspacientes.servicio.EpisodioNoEncontradoException;
import com.sos.mspacientes.servicio.HistoriaClinicaService;
import com.sos.mspacientes.servicio.NotificacionesService;
import com.sos.mspacientes.servicio.PdfNoGeneradoException;
import com.sos.mspacientes.servicio.TriageGateway;
import com.sos.mspacientes.servicio.TriageNoDisponibleException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pacientes")
public class PacienteController {

    private final PacienteRepository pacienteRepository;
    private final EstadoPacienteRepository estadoPacienteRepository;
    private final EmailService emailService;
    private final HistoriaClinicaService historiaClinicaService;
    private final NotificacionesService notificacionesService;
    private final TriageGateway triageGateway;

    public PacienteController(PacienteRepository pacienteRepository,
                               EstadoPacienteRepository estadoPacienteRepository,
                               EmailService emailService,
                               HistoriaClinicaService historiaClinicaService,
                               NotificacionesService notificacionesService,
                               TriageGateway triageGateway) {
        this.pacienteRepository = pacienteRepository;
        this.estadoPacienteRepository = estadoPacienteRepository;
        this.emailService = emailService;
        this.historiaClinicaService = historiaClinicaService;
        this.notificacionesService = notificacionesService;
        this.triageGateway = triageGateway;
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
    public ResponseEntity<?> registrarSignosVitales(
            @Valid @RequestBody SignosVitalesRequest request,
            @AuthenticationPrincipal UsuarioJwt usuario,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        if (usuario == null || usuario.idPersonal() == null) {
            return ResponseEntity.status(401).body("No autenticado");
        }

        try {
            // El episodio debe existir en ms-triage antes de aceptar la toma.
            triageGateway.obtenerRegistro(request.getIdRegistro(), authorization);
        } catch (EpisodioNoEncontradoException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (TriageNoDisponibleException e) {
            return ResponseEntity.status(502).body(e.getMessage());
        }

        EstadoPaciente estadoPaciente = new EstadoPaciente();
        estadoPaciente.setIdRegistro(request.getIdRegistro());
        estadoPaciente.setFrecuenciaCardiaca(request.getFrecuenciaCardiaca());
        estadoPaciente.setPresionSistolica(request.getPresionSistolica());
        estadoPaciente.setPresionDiastolica(request.getPresionDiastolica());
        estadoPaciente.setTemperatura(request.getTemperatura());
        estadoPaciente.setSaturacion(request.getSaturacion());
        estadoPaciente.setFrecuenciaRespiratoria(request.getFrecuenciaRespiratoria());
        estadoPaciente.setGlucemia(request.getGlucemia());
        estadoPaciente.setDescripcionSintoma(request.getDescripcionSintoma());
        estadoPaciente.setIdPersonal(usuario.idPersonal());
        estadoPaciente.setFechaHoraToma(java.time.LocalDateTime.now());
        estadoPaciente.setIdEstado(1);

        return ResponseEntity.ok(estadoPacienteRepository.save(estadoPaciente));
    }

    @GetMapping("/signos-vitales/registro/{idRegistro}")
    public List<EstadoPaciente> obtenerSignosVitalesPorRegistro(@PathVariable Integer idRegistro) {
        return estadoPacienteRepository.findByIdRegistro(idRegistro);
    }

    @PostMapping("/{id}/enviar-historia")
    public ResponseEntity<?> enviarHistoria(@PathVariable Integer id,
                                            @RequestBody EnviarHistoriaRequest request,
                                            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                            String authorization) {
        return pacienteRepository.findById(id).map(paciente -> {
            if (paciente.getEmail() == null || paciente.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body("Este paciente no tiene correo registrado");
            }
            try {
                if (request.getIdRegistro() != null) {
                    // Flujo nuevo: el backend arma la historia clinica y adjunta el PDF.
                    notificacionesService.enviarHistoriaClinica(paciente, request.getIdRegistro(),
                            null, authorization);
                } else {
                    // Compatibilidad con el frontend antiguo que enviaba texto armado en el navegador.
                    java.util.Map<String, Object> modelo = new java.util.LinkedHashMap<>();
                    modelo.put("titulo", "Tu historia clínica - SOS");
                    modelo.put("contenido", request.getContenido() == null ? "" : request.getContenido());
                    modelo.put("nota", "Documento enviado por el equipo de atención de SOS Clinic.");
                    modelo.put("destinatario", paciente.getEmail());
                    emailService.enviarHtml(paciente.getEmail(), "Tu historia clínica - SOS",
                            emailService.renderizar("plantillas/documento-texto", modelo));
                }
                return ResponseEntity.ok().build();
            } catch (EmailNoEnviadoException e) {
                return ResponseEntity.status(502).body(e.getMessage());
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/enviar-cita-teleconsulta")
    public ResponseEntity<?> enviarCitaTeleconsulta(@PathVariable Integer id,
                                                      @Valid @RequestBody ConfirmacionTeleconsultaRequest request) {
        return pacienteRepository.findById(id).map(paciente -> {
            if (paciente.getEmail() == null || paciente.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body("Este paciente no tiene correo registrado");
            }
            try {
                notificacionesService.enviarCitaPaciente(paciente, request.getFechaHora(), request.getEnlace(),
                        request.getFechaHoraInicio(), request.getFechaHoraFin());
                return ResponseEntity.ok().build();
            } catch (EmailNoEnviadoException e) {
                return ResponseEntity.status(502).body(e.getMessage());
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * PDF de la historia clinica bajo demanda: lo descarga el navegador
     * desde la pantalla de seguimiento y tambien lo usa el correo.
     */
    @GetMapping("/historias/{idRegistro}/pdf")
    public ResponseEntity<?> descargarHistoriaPdf(
            @PathVariable Integer idRegistro,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        try {
            HistoriaClinicaService.PdfHistoria pdf = historiaClinicaService.generarPdf(idRegistro, authorization);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + pdf.nombreArchivo() + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(pdf.contenido());
        } catch (EpisodioNoEncontradoException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (TriageNoDisponibleException | PdfNoGeneradoException e) {
            return ResponseEntity.status(502).body(e.getMessage());
        }
    }

    @GetMapping("/buscar")
    public List<Paciente> buscar(@RequestParam String q) {
        if (q == null || q.isBlank()) return List.of();
        return pacienteRepository.findTop10ByCedulaContainingOrderByCedula(q.trim());
    }
}