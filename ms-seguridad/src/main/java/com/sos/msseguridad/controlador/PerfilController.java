/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.controlador;

import com.sos.msseguridad.dto.PerfilRequest;
import com.sos.msseguridad.modelo.Perfil;
import com.sos.msseguridad.repositorio.PerfilRepository;
import com.sos.msseguridad.servicio.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/perfiles")
public class PerfilController {

    private final PerfilRepository perfilRepository;
    private final AuditoriaService auditoriaService;

    public PerfilController(PerfilRepository perfilRepository, AuditoriaService auditoriaService) {
        this.perfilRepository = perfilRepository;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<Perfil> listar() {
        return perfilRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody PerfilRequest request) {
        Perfil perfil = new Perfil();
        perfil.setPerfil(request.getPerfil());
        perfil.setIdEstado(1);
        Perfil guardado = perfilRepository.save(perfil);

        auditoriaService.registrarAccionActual("CREAR", "Perfil: " + guardado.getPerfil());
        return ResponseEntity.ok(guardado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @Valid @RequestBody PerfilRequest request) {
        return perfilRepository.findById(id).map(perfil -> {
            String nombreAnterior = perfil.getPerfil();
            perfil.setPerfil(request.getPerfil());
            Perfil guardado = perfilRepository.save(perfil);

            auditoriaService.registrarAccionActual("ACTUALIZAR", "Perfil: " + nombreAnterior + " -> " + guardado.getPerfil());
            return ResponseEntity.ok(guardado);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        Perfil perfil = perfilRepository.findById(id).orElse(null);
        if (perfil == null) return ResponseEntity.notFound().build();

        String nombre = perfil.getPerfil();
        try {
            perfilRepository.deleteById(id);
            auditoriaService.registrarAccionActual("ELIMINAR", "Perfil: " + nombre);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(409).body("No se puede eliminar: hay usuarios o actividades asignadas a este perfil");
        }
    }
}
