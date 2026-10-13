/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msiaagente.ms_ia_agente.controlador;



import com.sos.msiaagente.ms_ia_agente.groq.GroqCliente;
import com.sos.msiaagente.ms_ia_agente.groq.WhisperCliente;
import com.sos.msiaagente.ms_ia_agente.servicio.AgenteService;
import com.sos.msiaagente.ms_ia_agente.servicio.AgenteService.Pregunta;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/agente")
public class AgenteController {

    private final AgenteService agente;
    private final WhisperCliente whisper;

    public AgenteController(AgenteService agente, WhisperCliente whisper) {
        this.agente = agente;
        this.whisper = whisper;
    }

    @PostMapping("/preguntar")
    public ResponseEntity<?> preguntar(@RequestBody Pregunta p,
                                        @RequestHeader(value = "Authorization", required = false) String auth) {
        if (p == null || p.mensaje() == null || p.mensaje().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Escribe tu pregunta."));
        }
        try {
            return ResponseEntity.ok(agente.responder(p, auth));
        } catch (GroqCliente.AgenteException e) {
            return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/transcribir", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> transcribir(@RequestBody byte[] audio,
                                          @RequestHeader(value = "Content-Type", required = false) String tipo) {
        String ext = "webm";
        if (tipo != null && tipo.contains("ogg")) ext = "ogg";
        else if (tipo != null && (tipo.contains("mp4") || tipo.contains("m4a"))) ext = "m4a";

        try {
            return ResponseEntity.ok(Map.of("texto", whisper.transcribir(audio, ext)));
        } catch (GroqCliente.AgenteException e) {
            return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
        }
    }
}