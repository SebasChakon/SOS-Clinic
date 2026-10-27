package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.modelo.Sesion;
import com.sos.msseguridad.repositorio.SesionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Crea, renueva y revoca las sesiones de aplicacion.
 *
 * El JWT de acceso es corto; el refresh token es opaco, se guarda hasheado y
 * se rota en cada renovacion. La sesion vence por inactividad o por el tope
 * absoluto, lo que ocurra primero.
 */
@Service
public class SesionService {

    @Value("${app.session.idle-ms:28800000}")
    private long idleMs;

    @Value("${app.session.max-ms:43200000}")
    private long maxMs;

    private final SesionRepository sesionRepository;
    private final SecureRandom random = new SecureRandom();

    public SesionService(SesionRepository sesionRepository) {
        this.sesionRepository = sesionRepository;
    }

    @PostConstruct
    void validarDuraciones() {
        if (idleMs <= 0 || maxMs <= 0 || idleMs > maxMs) {
            throw new IllegalStateException(
                    "app.session.idle-ms debe ser positivo y no superar app.session.max-ms");
        }
    }

    public record SesionCreada(String token, LocalDateTime fechaExpira, LocalDateTime fechaMaxima) {}

    public record SesionRenovada(Personal personal, String token,
                                 LocalDateTime fechaExpira, LocalDateTime fechaMaxima) {}

    public SesionCreada crear(Personal personal, String ip, String userAgent) {
        if (personal == null) {
            throw new IllegalArgumentException("El personal es obligatorio");
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaMaxima = ahora.plus(Duration.ofMillis(maxMs));
        LocalDateTime fechaExpira = min(ahora.plus(Duration.ofMillis(idleMs)), fechaMaxima);
        String token = tokenAleatorio();

        Sesion sesion = new Sesion();
        sesion.setPersonal(personal);
        sesion.setTokenHash(hash(token));
        sesion.setFechaCreacion(ahora);
        sesion.setUltimoUso(ahora);
        sesion.setFechaExpira(fechaExpira);
        sesion.setFechaMaxima(fechaMaxima);
        sesion.setRevocada(false);
        sesion.setIpCreacion(recortar(ip, 45));
        sesion.setUserAgent(recortar(userAgent, 200));
        sesionRepository.save(sesion);

        return new SesionCreada(token, fechaExpira, fechaMaxima);
    }

    public SesionRenovada renovar(String token) {
        if (token == null || token.isBlank()) {
            throw sesionInvalida();
        }

        Sesion sesion = sesionRepository.findByTokenHashAndRevocadaFalse(hash(token.trim()))
                .orElseThrow(this::sesionInvalida);

        LocalDateTime ahora = LocalDateTime.now();
        if (sesion.getFechaExpira().isBefore(ahora) || sesion.getFechaMaxima().isBefore(ahora)) {
            sesion.setRevocada(true);
            sesion.setMotivoRevocacion("EXPIRADA");
            sesionRepository.save(sesion);
            throw sesionInvalida();
        }

        String nuevoToken = tokenAleatorio();
        sesion.setTokenHash(hash(nuevoToken));
        sesion.setUltimoUso(ahora);
        sesion.setFechaExpira(min(ahora.plus(Duration.ofMillis(idleMs)), sesion.getFechaMaxima()));
        sesionRepository.save(sesion);

        return new SesionRenovada(sesion.getPersonal(), nuevoToken,
                sesion.getFechaExpira(), sesion.getFechaMaxima());
    }

    public void revocar(String token, String motivo) {
        if (token == null || token.isBlank()) {
            return;
        }
        sesionRepository.findByTokenHashAndRevocadaFalse(hash(token.trim()))
                .ifPresent(sesion -> {
                    sesion.setRevocada(true);
                    sesion.setMotivoRevocacion(recortar(motivo, 40));
                    sesionRepository.save(sesion);
                });
    }

    public int revocarTodas(Personal personal, String motivo) {
        if (personal == null || personal.getIdPersonal() == null) {
            return 0;
        }
        return sesionRepository.revocarTodas(personal.getIdPersonal(), recortar(motivo, 40));
    }

    private BadCredentialsException sesionInvalida() {
        return new BadCredentialsException("Sesion expirada. Vuelve a iniciar sesion.");
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }

    private String tokenAleatorio() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 en hexadecimal. El token tiene 256 bits de entropia. */
    public static String hash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
