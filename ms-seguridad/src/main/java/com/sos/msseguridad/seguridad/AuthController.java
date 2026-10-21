/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.dto.CambioEmailRequest;
import com.sos.msseguridad.dto.CambiarClaveRequest;
import com.sos.msseguridad.dto.ConfirmarEmailRequest;
import com.sos.msseguridad.dto.Desactivar2faRequest;
import com.sos.msseguridad.dto.LoginRequest;
import com.sos.msseguridad.dto.LoginResponse;
import com.sos.msseguridad.dto.PoliticaClaveResponse;
import com.sos.msseguridad.dto.ProgramarTeleconsultaRequest;
import com.sos.msseguridad.dto.RecuperarClaveRequest;
import com.sos.msseguridad.dto.Reenviar2faRequest;
import com.sos.msseguridad.dto.RestablecerClaveRequest;
import com.sos.msseguridad.dto.SolicitarDesactivar2faRequest;
import com.sos.msseguridad.dto.ValidarTokenRequest;
import com.sos.msseguridad.dto.VerificarCodigoRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PoliticaClaves politicaClaves;

    public AuthController(AuthService authService, PoliticaClaves politicaClaves) {
        this.authService = authService;
        this.politicaClaves = politicaClaves;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                    .body(e.getMessage());
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/verificar-2fa")
    public ResponseEntity<?> verificarCodigo(@Valid @RequestBody VerificarCodigoRequest request) {
        try {
            return ResponseEntity.ok(authService.verificarCodigo(request));
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                    .body(e.getMessage());
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/reenviar-2fa")
    public ResponseEntity<?> reenviarCodigo(@Valid @RequestBody Reenviar2faRequest request) {
        return ResponseEntity.ok(authService.reenviarCodigo(request));
    }

    @GetMapping("/politica-clave")
    public PoliticaClaveResponse politicaClave() {
        return new PoliticaClaveResponse(
                PoliticaClaves.LONGITUD_MINIMA,
                PoliticaClaves.LONGITUD_MAXIMA,
                PoliticaClaves.SIMBOLOS,
                List.of(
                        "Minimo 8 caracteres y maximo 64.",
                        "Al menos una mayuscula, una minuscula, un numero y un simbolo.",
                        "Simbolos permitidos: ! ? # $ % . * @ _ - +",
                        "Sin tres caracteres iguales seguidos (aaa, 111).",
                        "Sin secuencias como abc, 123, cba o 321.",
                        "Sin secuencias de teclado como qwerty o asdf.",
                        "Sin palabras comunes ni el nombre del sistema.",
                        "Sin tus datos personales: nombre, apellido, usuario, correo, cedula o telefono."
                ));
    }

    @PostMapping("/recuperar-clave")
    public ResponseEntity<?> recuperarClave(@Valid @RequestBody RecuperarClaveRequest request,
                                            HttpServletRequest http) {
        authService.recuperarClave(request, ip(http), userAgent(http));
        return ResponseEntity.ok(Map.of("message", AuthService.mensajeRecuperacionGenerico()));
    }

    @PostMapping("/validar-token-recuperacion")
    public ResponseEntity<?> validarToken(@Valid @RequestBody ValidarTokenRequest request) {
        return ResponseEntity.ok(authService.validarTokenRecuperacion(request.getToken()));
    }

    @PostMapping("/restablecer-clave")
    public ResponseEntity<?> restablecerClave(@Valid @RequestBody RestablecerClaveRequest request) {
        authService.restablecerClave(request);
        return ResponseEntity.ok(Map.of("message", "Clave actualizada. Ya puedes iniciar sesion."));
    }

    @PostMapping("/confirmar-email")
    public ResponseEntity<?> confirmarEmail(@Valid @RequestBody ConfirmarEmailRequest request) {
        authService.confirmarCambioEmail(request);
        return ResponseEntity.ok(Map.of("message", "Correo actualizado correctamente."));
    }

    @PostMapping("/cambiar-clave")
    public ResponseEntity<?> cambiarClave(@Valid @RequestBody CambiarClaveRequest request,
                                          Principal principal) {
        return ResponseEntity.ok(authService.cambiarClave(principal.getName(), request));
    }

    @GetMapping("/mi-cuenta")
    public ResponseEntity<?> miCuenta(Principal principal) {
        return ResponseEntity.ok(authService.miCuenta(principal.getName()));
    }

    @PostMapping("/2fa/activar")
    public ResponseEntity<?> activar2fa(Principal principal) {
        authService.activar2fa(principal.getName());
        return ResponseEntity.ok(Map.of("message", "Verificacion en dos pasos activada."));
    }

    @PostMapping("/2fa/desactivar/solicitar")
    public ResponseEntity<?> solicitarDesactivar2fa(@Valid @RequestBody SolicitarDesactivar2faRequest request,
                                                    Principal principal) {
        String challenge = authService.solicitarDesactivar2fa(principal.getName(), request.getClaveActual());
        return ResponseEntity.ok(Map.of("challenge", challenge,
                "message", "Te enviamos un codigo al correo registrado."));
    }

    @PostMapping("/2fa/desactivar")
    public ResponseEntity<?> desactivar2fa(@Valid @RequestBody Desactivar2faRequest request,
                                           Principal principal) {
        authService.desactivar2fa(principal.getName(), request);
        return ResponseEntity.ok(Map.of("message", "Verificacion en dos pasos desactivada."));
    }

    @PostMapping("/solicitar-cambio-email")
    public ResponseEntity<?> solicitarCambioEmail(@Valid @RequestBody CambioEmailRequest request,
                                                  Principal principal, HttpServletRequest http) {
        authService.solicitarCambioEmail(principal.getName(), request, ip(http), userAgent(http));
        return ResponseEntity.ok(Map.of("message",
                "Te enviamos un enlace al correo nuevo. Revisa tambien el aviso en el correo anterior."));
    }

    @PostMapping("/programar-teleconsulta")
    public ResponseEntity<?> programarTeleconsulta(@Valid @RequestBody ProgramarTeleconsultaRequest request,
                                                    Principal principal) {
        return ResponseEntity.ok(authService.enviarConfirmacionTeleconsulta(
                principal.getName(), request.getNombrePaciente()));
    }

    private static String ip(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
