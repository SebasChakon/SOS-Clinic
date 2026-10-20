package com.sos.msiaagente.ms_ia_agente.controlador;

import com.sos.msiaagente.ms_ia_agente.groq.GroqCliente;
import com.sos.msiaagente.ms_ia_agente.groq.WhisperCliente;
import com.sos.msiaagente.ms_ia_agente.seguridad.UsuarioJwt;
import com.sos.msiaagente.ms_ia_agente.servicio.AgenteRateLimitService;
import com.sos.msiaagente.ms_ia_agente.servicio.AgenteService;
import com.sos.msiaagente.ms_ia_agente.servicio.AgenteService.Pregunta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/agente")
public class AgenteController {

    private final AgenteService agente;
    private final WhisperCliente whisper;
    private final AgenteRateLimitService rateLimit;

    @Value("${agente.audio.max-bytes:10485760}")
    private int maxAudioBytes;

    public AgenteController(AgenteService agente, WhisperCliente whisper,
                            AgenteRateLimitService rateLimit) {
        this.agente = agente;
        this.whisper = whisper;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/preguntar")
    public ResponseEntity<?> preguntar(@RequestBody Pregunta p,
                                        @RequestHeader(value = "Authorization", required = false) String auth,
                                        @AuthenticationPrincipal UsuarioJwt usuario) {
        if (usuario == null) {
            return ResponseEntity.status(401).body(Map.of("message", "No autenticado"));
        }
        if (!rateLimit.permitir("chat:" + usuario.usuario(), 20, Duration.ofMinutes(1))) {
            return ResponseEntity.status(429)
                    .header("Retry-After", "60")
                    .body(Map.of("message", "Demasiadas solicitudes. Intenta de nuevo en un minuto."));
        }
        if (p == null || p.mensaje() == null || p.mensaje().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Escribe tu pregunta."));
        }
        try {
            return ResponseEntity.ok(agente.responder(p, auth, usuario));
        } catch (GroqCliente.AgenteException e) {
            return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/transcribir", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> transcribir(@RequestBody byte[] audio,
                                          @RequestHeader(value = "Content-Type", required = false) String tipo,
                                          @AuthenticationPrincipal UsuarioJwt usuario) {
        if (usuario == null) {
            return ResponseEntity.status(401).body(Map.of("message", "No autenticado"));
        }
        if (!rateLimit.permitir("voz:" + usuario.usuario(), 5, Duration.ofMinutes(1))) {
            return ResponseEntity.status(429)
                    .header("Retry-After", "60")
                    .body(Map.of("message", "Demasiadas solicitudes de voz. Intenta de nuevo en un minuto."));
        }
        if (audio == null || audio.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "El audio esta vacio."));
        }
        if (audio.length > maxAudioBytes) {
            return ResponseEntity.status(413)
                    .body(Map.of("message", "El audio supera el limite permitido."));
        }

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
