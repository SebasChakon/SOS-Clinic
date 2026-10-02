/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.controlador;

import com.sos.msseguridad.dto.PerfilActividadRequest;
import com.sos.msseguridad.modelo.Actividad;
import com.sos.msseguridad.modelo.Perfil;
import com.sos.msseguridad.modelo.PerfilActividad;
import com.sos.msseguridad.repositorio.ActividadRepository;
import com.sos.msseguridad.repositorio.PerfilActividadRepository;
import com.sos.msseguridad.repositorio.PerfilRepository;
import com.sos.msseguridad.servicio.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/perfil-actividad")
public class PerfilActividadController {

    private final PerfilActividadRepository perfilActividadRepository;
    private final PerfilRepository perfilRepository;
    private final ActividadRepository actividadRepository;
    private final AuditoriaService auditoriaService;

    public PerfilActividadController(PerfilActividadRepository perfilActividadRepository,
                                      PerfilRepository perfilRepository,
                                      ActividadRepository actividadRepository,
                                      AuditoriaService auditoriaService) {
        this.perfilActividadRepository = perfilActividadRepository;
        this.perfilRepository = perfilRepository;
        this.actividadRepository = actividadRepository;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<PerfilActividad> listar() {
        return perfilActividadRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> asignar(@Valid @RequestBody PerfilActividadRequest request) {
        Perfil perfil = perfilRepository.findById(request.getIdPerfil()).orElse(null);
        Actividad actividad = actividadRepository.findById(request.getIdActividad()).orElse(null);

        if (perfil == null || actividad == null) {
            return ResponseEntity.badRequest().body("Perfil o actividad no encontrados");
        }

        boolean yaExiste = perfilActividadRepository.findByPerfil_IdPerfil(perfil.getIdPerfil())
                .stream().anyMatch(pa -> pa.getActividad().getIdActividad().equals(actividad.getIdActividad()));
        if (yaExiste) {
            return ResponseEntity.status(409).body("Esa actividad ya esta asignada a ese perfil");
        }

        PerfilActividad pa = new PerfilActividad();
        pa.setPerfil(perfil);
        pa.setActividad(actividad);
        pa.setIdEstado(1);
        PerfilActividad guardado = perfilActividadRepository.save(pa);

        auditoriaService.registrarAccionActual("CREAR", "Asignacion: " + perfil.getPerfil() + " -> " + actividad.getNombreActividad());
        return ResponseEntity.ok(guardado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> quitar(@PathVariable Integer id) {
        PerfilActividad pa = perfilActividadRepository.findById(id).orElse(null);
        if (pa == null) return ResponseEntity.notFound().build();

        String detalle = pa.getPerfil().getPerfil() + " -> " + pa.getActividad().getNombreActividad();
        perfilActividadRepository.deleteById(id);

        auditoriaService.registrarAccionActual("ELIMINAR", "Asignacion: " + detalle);
        return ResponseEntity.noContent().build();
    }
}