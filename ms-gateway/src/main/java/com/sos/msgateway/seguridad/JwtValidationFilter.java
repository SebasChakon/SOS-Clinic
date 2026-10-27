package com.sos.msgateway.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Defensa en profundidad: el gateway rechaza peticiones sin JWT valido antes
 * de enrutarlas. Los servicios de negocio vuelven a validar el token.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtValidationFilter extends OncePerRequestFilter {

    private final SecretKey signingKey;

    public JwtValidationFilter(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET no esta configurado o tiene menos de 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String ruta = request.getRequestURI();
        return "/ms-seguridad/auth/login".equals(ruta)
                || "/ms-seguridad/auth/verificar-2fa".equals(ruta)
                || "/ms-seguridad/auth/reenviar-2fa".equals(ruta)
                || "/ms-seguridad/auth/recuperar-clave".equals(ruta)
                || "/ms-seguridad/auth/validar-token-recuperacion".equals(ruta)
                || "/ms-seguridad/auth/restablecer-clave".equals(ruta)
                || "/ms-seguridad/auth/confirmar-email".equals(ruta)
                || "/ms-seguridad/auth/politica-clave".equals(ruta)
                || "/ms-seguridad/auth/refrescar".equals(ruta)
                || "/ms-seguridad/auth/logout".equals(ruta)
                || "/ms-seguridad/error".equals(ruta)
                || "/error".equals(ruta);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            rechazar(response);
            return;
        }

        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(header.substring(7)).getPayload();

            if (Boolean.TRUE.equals(claims.get("ccp", Boolean.class))
                    && !"/ms-seguridad/auth/cambiar-clave".equals(request.getRequestURI())) {
                rechazarCambioClave(response);
                return;
            }
        } catch (Exception e) {
            rechazar(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void rechazarCambioClave(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":\"CAMBIO_CLAVE_REQUERIDO\",\"message\":\"Debes cambiar la clave temporal antes de continuar.\"}");
    }

    private void rechazar(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"No autorizado\"}");
    }
}
