package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.modelo.TokenRecuperacion;
import com.sos.msseguridad.repositorio.TokenRecuperacionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Emite y valida tokens opacos de un solo uso.
 *
 * Los tokens de activacion, recuperacion y cambio de correo se guardan
 * unicamente como SHA-256. Como el token tiene 256 bits de entropia no
 * hace falta un hash lento: basta con poder buscarlo en una consulta.
 */
@Service
public class RecuperacionClaveService {

    public static final String TIPO_RECUPERACION = "RECUPERACION";
    public static final String TIPO_ACTIVACION = "ACTIVACION";
    public static final String TIPO_CAMBIO_EMAIL = "CAMBIO_EMAIL";

    private static final Duration DURACION_RECUPERACION = Duration.ofMinutes(30);
    private static final Duration DURACION_ACTIVACION = Duration.ofHours(72);
    private static final Duration DURACION_CAMBIO_EMAIL = Duration.ofMinutes(30);

    private final TokenRecuperacionRepository tokenRepository;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.frontend.url:http://127.0.0.1:5500}")
    private String frontendUrl;

    public RecuperacionClaveService(TokenRecuperacionRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public record TokenGenerado(String token, String tipo, LocalDateTime expira) {}

    public TokenGenerado generar(Personal personal, String tipo, String ip, String userAgent) {
        if (personal == null) {
            throw new IllegalArgumentException("El personal es obligatorio");
        }
        tokenRepository.invalidarPendientes(personal.getIdPersonal(), tipo);

        String token = tokenAleatorio();
        LocalDateTime expira = LocalDateTime.now().plus(duracion(tipo));

        TokenRecuperacion registro = new TokenRecuperacion();
        registro.setPersonal(personal);
        registro.setTipo(tipo);
        registro.setTokenHash(hash(token));
        registro.setFechaCreacion(LocalDateTime.now());
        registro.setFechaExpira(expira);
        registro.setUsado(false);
        registro.setIpSolicitud(recortar(ip, 45));
        registro.setUserAgent(recortar(userAgent, 200));
        tokenRepository.save(registro);

        return new TokenGenerado(token, tipo, expira);
    }

    public TokenRecuperacion validar(String token, String tipoEsperado) {
        if (token == null || token.isBlank()) {
            throw enlaceInvalido();
        }
        TokenRecuperacion registro = tokenRepository
                .findByTokenHashAndUsadoFalse(hash(token.trim()))
                .orElseThrow(this::enlaceInvalido);

        if (registro.getFechaExpira().isBefore(LocalDateTime.now())) {
            throw enlaceInvalido();
        }
        if (tipoEsperado != null && !tipoEsperado.equals(registro.getTipo())) {
            throw enlaceInvalido();
        }
        return registro;
    }

    public void marcarUsado(TokenRecuperacion token) {
        if (token == null) return;
        token.setUsado(true);
        tokenRepository.save(token);
    }

    public void invalidarPendientes(Personal personal, String tipo) {
        if (personal == null) return;
        tokenRepository.invalidarPendientes(personal.getIdPersonal(), tipo);
    }

    public void invalidarTodos(Personal personal) {
        invalidarPendientes(personal, TIPO_RECUPERACION);
        invalidarPendientes(personal, TIPO_ACTIVACION);
        invalidarPendientes(personal, TIPO_CAMBIO_EMAIL);
    }

    public String enlace(String token) {
        String base = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
        return base + "/restablecer-clave.html?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    public static String hash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private Duration duracion(String tipo) {
        return switch (tipo) {
            case TIPO_ACTIVACION -> DURACION_ACTIVACION;
            case TIPO_CAMBIO_EMAIL -> DURACION_CAMBIO_EMAIL;
            default -> DURACION_RECUPERACION;
        };
    }

    private String tokenAleatorio() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ResponseStatusException enlaceInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El enlace no es valido, ya fue usado o expiro.");
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null) return null;
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
