/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msiaagente.ms_ia_agente.groq;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
public class GroqCliente {

    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";

    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${groq.api.key:}") private String apiKey;
    @Value("${groq.modelo}") private String modelo;
    @Value("${groq.modelo.respaldo:}") private String modeloRespaldo;
    @Value("${agente.razonamiento:low}") private String razonamiento;
    @Value("${agente.maxTokensRespuesta:700}") private int maxTokens;

    public GroqCliente(JsonMapper json) { this.json = json; }

    public static class AgenteException extends RuntimeException {
        public final int status;
        public AgenteException(int status, String mensaje) { super(mensaje); this.status = status; }
    }

    public Map<String, Object> chat(List<Map<String, Object>> mensajes, List<Map<String, Object>> herramientas) {
        if (apiKey == null || !apiKey.startsWith("gsk_")) {
            throw new AgenteException(503, "El asistente no está configurado (falta la clave de Groq).");
        }

        List<String> modelos = new ArrayList<>();
        modelos.add(modelo);
        if (modeloRespaldo != null && !modeloRespaldo.isBlank()) modelos.add(modeloRespaldo);

        AgenteException ultimo = null;
        for (String m : modelos) {
            try {
                return llamar(m, mensajes, herramientas);
            } catch (AgenteException e) {
                ultimo = e;
                // Clave mala o pregunta demasiado larga: el modelo de respaldo no lo arregla
                if (e.status == 401 || e.status == 403 || e.status == 413 || e.status == 503) throw e;
            }
        }
        throw ultimo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> llamar(String m, List<Map<String, Object>> mensajes, List<Map<String, Object>> herramientas) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", m);
        cuerpo.put("messages", mensajes);
        cuerpo.put("temperature", 0.2);
        cuerpo.put("max_completion_tokens", maxTokens);
        cuerpo.put("reasoning_effort", razonamiento);
        cuerpo.put("include_reasoning", false);
        if (herramientas != null && !herramientas.isEmpty()) {
            cuerpo.put("tools", herramientas);
            cuerpo.put("tool_choice", "auto");
        }

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int s = resp.statusCode();

            if (s == 200) return json.readValue(resp.body(), Map.class);
            if (s == 429) {
                String espera = resp.headers().firstValue("retry-after").map(x -> x + " segundos").orElse("unos segundos");
                throw new AgenteException(429, "El asistente está saturado. Intenta de nuevo en " + espera + ".");
            }
            if (s == 401 || s == 403) throw new AgenteException(503, "El asistente no está configurado correctamente (clave inválida).");
            if (s == 413) throw new AgenteException(413, "La pregunta es demasiado larga.");
            throw new AgenteException(502, "El asistente no pudo responder en este momento.");
        } catch (AgenteException e) {
            throw e;
        } catch (Exception e) {
            throw new AgenteException(502, "El asistente no pudo responder en este momento.");
        }
    }
}
