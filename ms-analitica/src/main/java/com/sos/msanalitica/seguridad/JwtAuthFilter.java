package com.sos.msanalitica.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final SecretKey signingKey;

    public JwtAuthFilter(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET no esta configurado o tiene menos de 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = Jwts.parser().verifyWith(signingKey).build()
                        .parseSignedClaims(header.substring(7)).getPayload();
                String usuario = claims.getSubject();
                String perfil = claims.get("perfil", String.class);
                if (usuario != null && perfil != null && !perfil.isBlank()) {
                    var autenticacion = new UsernamePasswordAuthenticationToken(
                            usuario, null,
                            List.of(new SimpleGrantedAuthority(
                                    "ROLE_" + rolDePerfil(perfil))));
                    SecurityContextHolder.getContext().setAuthentication(autenticacion);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Los datos historicos usan "coordinador_turnos"; el esquema base usa
     * "coordinacion". Ambos deben resolver al mismo rol.
     */
    static String rolDePerfil(String perfil) {
        String p = perfil == null ? "" : perfil.trim().toLowerCase(Locale.ROOT);
        return switch (p) {
            case "administrador" -> "ADMINISTRADOR";
            case "coordinacion", "coordinador_turnos" -> "COORDINACION";
            case "medico", "doctor_triage" -> "MEDICO";
            case "triage" -> "TRIAGE";
            case "seguridad" -> "SEGURIDAD";
            default -> p.isEmpty() ? "USUARIO" : p.toUpperCase(Locale.ROOT).replace(' ', '_');
        };
    }
}
