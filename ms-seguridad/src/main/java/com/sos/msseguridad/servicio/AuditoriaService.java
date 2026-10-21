/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.servicio;

import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.modelo.RegistroAuditoria;
import com.sos.msseguridad.repositorio.PersonalRepository;
import com.sos.msseguridad.repositorio.RegistroAuditoriaRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditoriaService {

    private final RegistroAuditoriaRepository registroAuditoriaRepository;
    private final PersonalRepository personalRepository;

    public AuditoriaService(RegistroAuditoriaRepository registroAuditoriaRepository,
                             PersonalRepository personalRepository) {
        this.registroAuditoriaRepository = registroAuditoriaRepository;
        this.personalRepository = personalRepository;
    }

    // Usa el usuario autenticado en el token actual (lo pone el JwtAuthFilter)
    public void registrarAccionActual(String accion, String detalle) {
        String usuarioActor = SecurityContextHolder.getContext().getAuthentication().getName();
        registrar(usuarioActor, accion, detalle);
    }

    public void registrar(String usuarioActor, String accion, String detalle) {
        try {
            Personal personal = personalRepository.findByUsuarioIgnoreCase(usuarioActor).orElse(null);
            if (personal == null) return;

            RegistroAuditoria log = new RegistroAuditoria();
            log.setPersonal(personal);
            log.setAccion(accion.length() > 20 ? accion.substring(0, 20) : accion);
            log.setDetalle(detalle != null && detalle.length() > 100 ? detalle.substring(0, 100) : detalle);
            log.setFechaHora(LocalDateTime.now());
            log.setIdEstado(1);
            registroAuditoriaRepository.save(log);
        } catch (Exception e) {
            // La auditoria nunca debe tumbar la operacion principal si algo sale mal aqui
        }
    }
}
