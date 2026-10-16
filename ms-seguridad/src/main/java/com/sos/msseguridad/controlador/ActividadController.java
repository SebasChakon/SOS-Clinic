/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.controlador;

import com.sos.msseguridad.dto.ActividadRequest;
import com.sos.msseguridad.modelo.Actividad;
import com.sos.msseguridad.repositorio.ActividadRepository;
import com.sos.msseguridad.repositorio.PerfilActividadRepository;
import com.sos.msseguridad.servicio.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/actividades")
public class ActividadController {

    private final ActividadRepository actividadRepository;
    private final PerfilActividadRepository perfilActividadRepository;
    private final AuditoriaService auditoriaService;

    public ActividadController(ActividadRepository actividadRepository,
                               PerfilActividadRepository perfilActividadRepository,
                               AuditoriaService auditoriaService) {
        this.actividadRepository = actividadRepository;
        this.perfilActividadRepository = perfilActividadRepository;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<Actividad> listar() {
        return actividadRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody ActividadRequest request) {
        Actividad actividad = new Actividad();
        actividad.setNombreActividad(request.getNombreActividad());
        actividad.setEnlace(request.getEnlace());
        actividad.setIdEstado(1);
        Actividad guardada = actividadRepository.save(actividad);

        auditoriaService.registrarAccionActual("CREAR", "Actividad: " + guardada.getNombreActividad());
        return ResponseEntity.ok(guardada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @Valid @RequestBody ActividadRequest request) {
        return actividadRepository.findById(id).map(actividad -> {
            String nombreAnterior = actividad.getNombreActividad();
            actividad.setNombreActividad(request.getNombreActividad());
            actividad.setEnlace(request.getEnlace());
            Actividad guardada = actividadRepository.save(actividad);

            auditoriaService.registrarAccionActual("ACTUALIZAR", "Actividad: " + nombreAnterior + " -> " + guardada.getNombreActividad());
            return ResponseEntity.ok(guardada);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        Actividad actividad = actividadRepository.findById(id).orElse(null);
        if (actividad == null) return ResponseEntity.notFound().build();

        String nombre = actividad.getNombreActividad();

        List<String> perfiles = perfilActividadRepository.findByActividad_IdActividad(id).stream()
                .map(pa -> pa.getPerfil().getPerfil())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (!perfiles.isEmpty()) {
            return ResponseEntity.status(409).body(
                    "No se puede eliminar la actividad \"" + nombre + "\": esta asignada a los perfiles: "
                            + String.join(", ", perfiles) + ". Quitala primero de esos perfiles en la pestana de asignaciones.");
        }

        try {
            actividadRepository.deleteById(id);
            auditoriaService.registrarAccionActual("ELIMINAR", "Actividad: " + nombre);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(409).body(
                    "No se puede eliminar la actividad \"" + nombre + "\": esta referenciada en otro registro del sistema.");
        }
    }
}
