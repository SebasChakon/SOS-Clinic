package com.sos.mstriage.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private SecretKey signingKey;

    @PostConstruct
    void inicializar() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET no esta configurado o tiene menos de 32 bytes");
        }
        signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UsuarioJwt extraer(String token) {
        Claims claims = parse(token);
        String usuario = claims.getSubject();
        String perfil = claims.get("perfil", String.class);

        Object idCrudo = claims.get("idPersonal");
        Integer idPersonal = idCrudo instanceof Number n ? n.intValue() : null;

        Object actividadesCrudas = claims.get("actividades");
        List<String> actividades = actividadesCrudas instanceof List<?> lista
                ? lista.stream().map(String::valueOf).toList()
                : List.of();

        return new UsuarioJwt(usuario, idPersonal, perfil, actividades);
    }

    public static String rolDePerfil(String perfil) {
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
