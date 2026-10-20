package com.sos.mstriage.controlador;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validacion(MethodArgumentNotValidException e) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("Datos incompletos en la solicitud");
        return ResponseEntity.badRequest().body(Map.of("message", mensaje));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> jsonInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "El cuerpo de la solicitud no es JSON valido"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> noEncontrado(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "No se encontro el registro solicitado"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> datoInvalido(DataIntegrityViolationException e) {
        log.warn("Violacion de integridad de datos en ms-triage");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Los datos enviados no son validos o referencian un registro inexistente"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denegado(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "No tienes permisos para esta operacion"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> errorNoControlado(Exception e) {
        log.error("Error no controlado en ms-triage", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "No se pudo procesar la solicitud"));
    }
}
