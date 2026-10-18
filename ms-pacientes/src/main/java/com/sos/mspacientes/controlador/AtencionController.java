package com.sos.mspacientes.controlador;

import com.sos.mspacientes.dto.FinalizarAtencionRequest;
import com.sos.mspacientes.dto.ReenviarCorreosRequest;
import com.sos.mspacientes.servicio.AtencionService;
import com.sos.mspacientes.servicio.EpisodioNoEncontradoException;
import com.sos.mspacientes.servicio.SeguridadNoDisponibleException;
import com.sos.mspacientes.servicio.TriageNoDisponibleException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Alta completa de un episodio en una sola llamada: persiste el alta en
 * ms-triage y envia las notificaciones (teleconsulta + historia en PDF).
 * El JWT del profesional se recibe y se reenvia a ms-seguridad.
 */
@RestController
@RequestMapping("/pacientes/atenciones")
public class AtencionController {

    private final AtencionService atencionService;

    public AtencionController(AtencionService atencionService) {
        this.atencionService = atencionService;
    }

    @RequestMapping(value = "/{idRegistro}/finalizar", method = RequestMethod.POST)
    public ResponseEntity<?> finalizar(@PathVariable Integer idRegistro,
                                       @Valid @RequestBody FinalizarAtencionRequest request,
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                       String authorization) {
        try {
            return ResponseEntity.ok(atencionService.finalizar(idRegistro, request, authorization));
        } catch (EpisodioNoEncontradoException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (TriageNoDisponibleException e) {
            return ResponseEntity.status(502).body(e.getMessage());
        }
    }

    @RequestMapping(value = "/{idRegistro}/reenviar-correos", method = RequestMethod.POST)
    public ResponseEntity<?> reenviarCorreos(@PathVariable Integer idRegistro,
                                             @RequestBody ReenviarCorreosRequest request,
                                             @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                             String authorization) {
        try {
            return ResponseEntity.ok(atencionService.reenviarCorreos(idRegistro, request, authorization));
        } catch (EpisodioNoEncontradoException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (TriageNoDisponibleException | SeguridadNoDisponibleException e) {
            return ResponseEntity.status(502).body(e.getMessage());
        }
    }

    /** Mensajes de validacion claros (por ejemplo, diagnostico vacio). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> validacion(MethodArgumentNotValidException e) {
        FieldError error = e.getBindingResult().getFieldError();
        String mensaje = error != null ? error.getDefaultMessage() : "Datos incompletos en la solicitud";
        return ResponseEntity.badRequest().body(mensaje);
    }
}
