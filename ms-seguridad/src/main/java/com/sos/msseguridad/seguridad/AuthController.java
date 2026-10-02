/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.dto.LoginRequest;
import com.sos.msseguridad.dto.LoginResponse;
import com.sos.msseguridad.dto.ProgramarTeleconsultaRequest;
import com.sos.msseguridad.dto.VerificarCodigoRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }
    
    @PostMapping("/verificar-2fa")
    public ResponseEntity<?> verificarCodigo(@Valid @RequestBody VerificarCodigoRequest request) {
        try {
            return ResponseEntity.ok(authService.verificarCodigo(request));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/programar-teleconsulta")
    public ResponseEntity<?> programarTeleconsulta(@Valid @RequestBody ProgramarTeleconsultaRequest request,
                                                    Principal principal) {
        return ResponseEntity.ok(authService.enviarConfirmacionTeleconsulta(
                principal.getName(), request.getNombrePaciente()));
    }
}
