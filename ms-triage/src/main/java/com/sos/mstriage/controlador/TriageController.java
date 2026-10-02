/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mstriage.controlador;

import com.sos.mstriage.dto.RegistroTriageRequest;
import com.sos.mstriage.dto.UbicacionRequest;
import com.sos.mstriage.modelo.RegistroTriage;
import com.sos.mstriage.servicio.TriageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @PostMapping
    public ResponseEntity<?> registrarLlegada(@Valid @RequestBody RegistroTriageRequest request) {
        try {
            return ResponseEntity.ok(triageService.registrarLlegada(request));
        } catch (NoSuchElementException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id) {
        return triageService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/prioridad")
    public List<RegistroTriage> listaPriorizacion() {
        return triageService.obtenerListaPriorizacion();
    }

    @PutMapping("/{id}/en-atencion")
    public ResponseEntity<?> marcarEnAtencion(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(triageService.cambiarEstado(id, "En atencion"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PutMapping("/{id}/ubicacion")
    public ResponseEntity<?> actualizarUbicacion(@PathVariable Integer id,
                                                  @Valid @RequestBody UbicacionRequest request) {
        try {
            return ResponseEntity.ok(triageService.actualizarUbicacion(id, request.getUbicacion()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PutMapping("/{id}/atendido")
    public ResponseEntity<?> marcarAtendido(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(triageService.cambiarEstado(id, "Atendido"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }
}
