package com.sos.msseguridad.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @PostConstruct
    void validarSecreto() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET no esta configurado o tiene menos de 32 bytes");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generarToken(String usuario, Integer idPersonal, String perfil,
                               List<String> actividades) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("idPersonal", idPersonal);
        claims.put("perfil", perfil);
        claims.put("actividades", actividades == null ? List.of() : actividades);

        return Jwts.builder()
                .claims(claims)
                .subject(usuario)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String generarToken(String usuario, Integer idPersonal, String perfil) {
        return generarToken(usuario, idPersonal, perfil, List.of());
    }

    public String extraerUsuario(String token) {
        return extraerClaim(token, Claims::getSubject);
    }

    public String extraerPerfil(String token) {
        return extraerClaim(token, claims -> claims.get("perfil", String.class));
    }

    public Integer extraerIdPersonal(String token) {
        return extraerClaim(token, claims -> claims.get("idPersonal", Integer.class));
    }

    public List<String> extraerActividades(String token) {
        Object crudo = extraerClaim(token, claims -> claims.get("actividades"));
        if (crudo instanceof List<?> lista) {
            return lista.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public boolean esTokenValido(String token, String usuario) {
        final String usuarioDelToken = extraerUsuario(token);
        return usuario != null
                && usuario.equals(usuarioDelToken)
                && !esTokenExpirado(token);
    }

    private boolean esTokenExpirado(String token) {
        return extraerClaim(token, Claims::getExpiration).before(new Date());
    }

    public Claims parse(String token) {
        return extraerTodosLosClaims(token);
    }

    private <T> T extraerClaim(String token, Function<Claims, T> resolver) {
        Claims claims = extraerTodosLosClaims(token);
        return resolver.apply(claims);
    }

    private Claims extraerTodosLosClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Normaliza los nombres de perfil historicos a autoridades estables.
     * Asi "coordinador_turnos" y "coordinacion" producen el mismo rol.
     */
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
