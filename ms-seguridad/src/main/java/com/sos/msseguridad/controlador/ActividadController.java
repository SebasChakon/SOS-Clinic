/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.controlador;

import com.sos.msseguridad.dto.ActividadRequest;
import com.sos.msseguridad.modelo.Actividad;
import com.sos.msseguridad.repositorio.ActividadRepository;
import com.sos.msseguridad.servicio.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/actividades")
public class ActividadController {

    private final ActividadRepository actividadRepository;
    private final AuditoriaService auditoriaService;

    public ActividadController(ActividadRepository actividadRepository, AuditoriaService auditoriaService) {
        this.actividadRepository = actividadRepository;
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
        try {
            actividadRepository.deleteById(id);
            auditoriaService.registrarAccionActual("ELIMINAR", "Actividad: " + nombre);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(409).body("No se puede eliminar: esta actividad esta asignada a algun perfil");
        }
    }
}
