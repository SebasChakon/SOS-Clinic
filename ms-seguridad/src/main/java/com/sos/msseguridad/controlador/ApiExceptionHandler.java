package com.sos.msseguridad.controlador;

import com.sos.msseguridad.seguridad.PoliticaClaveException;
import com.sos.msseguridad.seguridad.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> jsonInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "El cuerpo de la solicitud no es JSON valido"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> credenciales(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", e.getMessage() == null ? "Credenciales invalidas" : e.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<?> limite(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(PoliticaClaveException.class)
    public ResponseEntity<?> politica(PoliticaClaveException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", e.getMessage(), "errores", e.getErrores()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> duplicado(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "El registro ya existe o viola una restriccion de unicidad"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denegado(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "No tienes permisos para esta operacion"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> estado(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() == null ? "Solicitud invalida" : e.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> errorNoControlado(Exception e) {
        log.error("Error no controlado en ms-seguridad", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "No se pudo procesar la solicitud"));
    }
}
