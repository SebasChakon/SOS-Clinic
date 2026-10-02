/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.controlador;

import com.sos.msseguridad.dto.RegistrarAuditoriaRequest;
import com.sos.msseguridad.servicio.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auditoria")
public class AuditoriaRegistroController {

    private final AuditoriaService auditoriaService;

    public AuditoriaRegistroController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(@Valid @RequestBody RegistrarAuditoriaRequest request) {
        // El usuario se toma del token JWT (no del body), asi nadie puede falsear quien hizo la accion
        auditoriaService.registrarAccionActual(request.getAccion(), request.getDetalle());
        return ResponseEntity.ok().build();
    }
}
