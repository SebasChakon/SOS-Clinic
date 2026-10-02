/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.controlador;

import com.sos.msseguridad.dto.AuditoriaResponse;
import com.sos.msseguridad.repositorio.RegistroAuditoriaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/auditoria")
public class AuditoriaController {

    private final RegistroAuditoriaRepository registroAuditoriaRepository;

    public AuditoriaController(RegistroAuditoriaRepository registroAuditoriaRepository) {
        this.registroAuditoriaRepository = registroAuditoriaRepository;
    }

    @GetMapping
    public List<AuditoriaResponse> listar() {
        return registroAuditoriaRepository.listarOrdenado().stream()
                .map(r -> new AuditoriaResponse(r.getIdLog(), r.getPersonal().getUsuario(), r.getAccion(), r.getDetalle(), r.getFechaHora()))
                .collect(Collectors.toList());
    }
}