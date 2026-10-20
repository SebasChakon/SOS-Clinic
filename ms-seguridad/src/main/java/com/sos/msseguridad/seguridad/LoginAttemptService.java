package com.sos.msseguridad.seguridad;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita intentos de login, de verificacion 2FA y de reenvio de codigos.
 *
 * Es un limite en memoria, adecuado para el prototipo de una sola instancia.
 * Si el sistema se despliega con varias instancias, debe sustituirse por
 * Redis o por columnas de intentos en base de datos.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_INTENTOS = 5;
    private static final Duration VENTANA_INTENTOS = Duration.ofMinutes(15);
    private static final Duration BLOQUEO = Duration.ofMinutes(15);

    private static final int MAX_REENVIOS = 3;
    private static final Duration VENTANA_REENVIO = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> intentos = new ConcurrentHashMap<>();
    private final Map<String, Instant> bloqueos = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> reenvios = new ConcurrentHashMap<>();

    public boolean estaBloqueado(String clave) {
        String k = normalizar(clave);
        Instant hasta = bloqueos.get(k);
        if (hasta == null) {
            return false;
        }
        if (hasta.isAfter(Instant.now())) {
            return true;
        }
        bloqueos.remove(k);
        intentos.remove(k);
        return false;
    }

    public long segundosRestantes(String clave) {
        Instant hasta = bloqueos.get(normalizar(clave));
        if (hasta == null) {
            return 0;
        }
        long segundos = Duration.between(Instant.now(), hasta).toSeconds();
        return Math.max(1, segundos);
    }

    public void registrarFallo(String clave) {
        String k = normalizar(clave);
        Deque<Instant> cola = intentos.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (cola) {
            limpiar(cola, VENTANA_INTENTOS);
            cola.addLast(Instant.now());
            if (cola.size() >= MAX_INTENTOS) {
                bloqueos.put(k, Instant.now().plus(BLOQUEO));
            }
        }
    }

    public void registrarExito(String clave) {
        String k = normalizar(clave);
        intentos.remove(k);
        bloqueos.remove(k);
    }

    public boolean puedeReenviar(String usuario) {
        String k = normalizar(usuario);
        Deque<Instant> cola = reenvios.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (cola) {
            limpiar(cola, VENTANA_REENVIO);
            return cola.size() < MAX_REENVIOS;
        }
    }

    public void registrarReenvio(String usuario) {
        String k = normalizar(usuario);
        Deque<Instant> cola = reenvios.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (cola) {
            limpiar(cola, VENTANA_REENVIO);
            cola.addLast(Instant.now());
        }
    }

    public long segundosParaReenvio(String usuario) {
        String k = normalizar(usuario);
        Deque<Instant> cola = reenvios.get(k);
        if (cola == null) {
            return 0;
        }
        synchronized (cola) {
            limpiar(cola, VENTANA_REENVIO);
            if (cola.size() < MAX_REENVIOS) {
                return 0;
            }
            Instant primero = cola.peekFirst();
            if (primero == null) {
                return 0;
            }
            return Math.max(1, Duration.between(Instant.now(),
                    primero.plus(VENTANA_REENVIO)).toSeconds());
        }
    }

    private static void limpiar(Deque<Instant> cola, Duration ventana) {
        Instant limite = Instant.now().minus(ventana);
        while (!cola.isEmpty() && cola.peekFirst().isBefore(limite)) {
            cola.removeFirst();
        }
    }

    private static String normalizar(String clave) {
        return clave == null ? "" : clave.trim().toLowerCase(Locale.ROOT);
    }
}
