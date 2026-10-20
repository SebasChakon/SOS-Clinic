package com.sos.msiaagente.ms_ia_agente.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rechaza por Content-Length los audios demasiado grandes antes de que
 * Spring lea el cuerpo completo en memoria.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TamanoAudioFilter extends OncePerRequestFilter {

    @Value("${agente.audio.max-bytes:10485760}")
    private long maxBytes;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && "/agente/transcribir".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String contentLength = request.getHeader("Content-Length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > maxBytes) {
                    response.setStatus(413);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"message\":\"El audio supera el limite permitido.\"}");
                    return;
                }
            } catch (NumberFormatException ignored) {
                // Si el header es invalido, el contenedor lo rechazara.
            }
        }
        filterChain.doFilter(request, response);
    }
}
