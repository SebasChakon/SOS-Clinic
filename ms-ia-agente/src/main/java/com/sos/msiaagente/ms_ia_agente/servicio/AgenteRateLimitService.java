package com.sos.msiaagente.ms_ia_agente.servicio;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite simple en memoria para el chat y la transcripcion de voz.
 * En un despliegue con varias instancias debe moverse a Redis.
 */
@Service
public class AgenteRateLimitService {

    private final Map<String, Deque<Instant>> eventos = new ConcurrentHashMap<>();

    public boolean permitir(String clave, int maximo, Duration ventana) {
        String k = clave == null ? "" : clave;
        Deque<Instant> cola = eventos.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (cola) {
            Instant limite = Instant.now().minus(ventana);
            while (!cola.isEmpty() && cola.peekFirst().isBefore(limite)) {
                cola.removeFirst();
            }
            if (cola.size() >= maximo) {
                return false;
            }
            cola.addLast(Instant.now());
            return true;
        }
    }
}
