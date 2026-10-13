/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msiaagente.ms_ia_agente.groq;



import com.sos.msiaagente.ms_ia_agente.groq.GroqCliente.AgenteException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class WhisperCliente {

    private static final String URL = "https://api.groq.com/openai/v1/audio/transcriptions";

    // Vocabulario del dominio: ayuda a Whisper con terminos medicos y nombres de pantallas
    private static final String PISTA = "SOS Clinic, triage, paciente, cédula, consultorio, signos vitales, "
            + "frecuencia cardíaca, frecuencia respiratoria, presión sistólica, diastólica, saturación, glucemia, "
            + "temperatura, Resucitación, Emergencia, Urgencia, lista de espera, seguimiento, alta, diagnóstico, tratamiento.";

    private static final List<String> ALUCINACIONES = List.of(
            "gracias por ver", "subtitulos", "suscribete", "amara.org", "gracias por su atencion");

    private static final double MAX_NO_SPEECH = 0.6;
    private static final double MIN_LOGPROB = -1.0;

    private final RestClient rest;
    private final JsonMapper json;

    @Value("${groq.api.key:}") private String apiKey;
    @Value("${groq.voz:whisper-large-v3,whisper-large-v3-turbo}") private String modelosVoz;

    public WhisperCliente(JsonMapper json) {
        this.json = json;
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(30));
        this.rest = RestClient.builder().requestFactory(fabrica).build();
    }

    public String transcribir(byte[] audio, String extension) {
        if (apiKey == null || !apiKey.startsWith("gsk_")) {
            throw new AgenteException(503, "El asistente no está configurado (falta la clave de Groq).");
        }
        if (audio == null || audio.length < 800) return ""; // demasiado corto para tener voz

        AgenteException ultimo = null;
        for (String m : modelosVoz.split(",")) {
            try {
                return llamar(m.trim(), audio, extension);
            } catch (AgenteException e) {
                ultimo = e;
                if (e.status == 503 || e.status == 413) throw e;
            }
        }
        throw ultimo;
    }

    private String llamar(String modelo, byte[] audio, String ext) {
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("file", new ByteArrayResource(audio) {
            @Override public String getFilename() { return "audio." + ext; }
        });
        cuerpo.add("model", modelo);
        cuerpo.add("language", "es");
        cuerpo.add("temperature", "0");
        cuerpo.add("response_format", "verbose_json");
        cuerpo.add("prompt", PISTA);

        String respuesta;
        try {
            respuesta = rest.post().uri(URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(cuerpo)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            int s = e.getStatusCode().value();
            if (s == 429) throw new AgenteException(429, "El reconocimiento de voz está saturado. Intenta en unos segundos.");
            if (s == 401 || s == 403) throw new AgenteException(503, "El asistente no está configurado correctamente (clave inválida).");
            if (s == 413) throw new AgenteException(413, "El audio es demasiado largo.");
            throw new AgenteException(502, "No pude transcribir el audio.");
        } catch (Exception e) {
            throw new AgenteException(502, "No pude transcribir el audio.");
        }
        return filtrar(respuesta);
    }

    /** Descarta segmentos sin voz, de baja confianza, repetidos o alucinaciones tipicas de Whisper. */
    @SuppressWarnings("unchecked")
    private String filtrar(String respuesta) {
        try {
            Map<String, Object> r = json.readValue(respuesta, Map.class);

            if (!(r.get("segments") instanceof List<?> segmentos) || segmentos.isEmpty()) {
                String t = String.valueOf(r.getOrDefault("text", "")).trim();
                return esAlucinacion(t) ? "" : t;
            }

            StringBuilder sb = new StringBuilder();
            String anterior = "";
            for (Object o : segmentos) {
                Map<String, Object> s = (Map<String, Object>) o;
                String t = String.valueOf(s.getOrDefault("text", "")).trim();
                double noSpeech = num(s.get("no_speech_prob"));
                double logprob = num(s.get("avg_logprob"));

                if (t.isEmpty()) continue;
                if (noSpeech > MAX_NO_SPEECH && logprob < -0.5) continue;
                if (logprob < MIN_LOGPROB) continue;
                if (t.equalsIgnoreCase(anterior)) continue;
                if (esAlucinacion(t)) continue;

                sb.append(t).append(' ');
                anterior = t;
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static double num(Object o) { return o instanceof Number n ? n.doubleValue() : 0; }

    private static boolean esAlucinacion(String t) {
        String n = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
        return ALUCINACIONES.stream().anyMatch(n::contains);
    }
}
