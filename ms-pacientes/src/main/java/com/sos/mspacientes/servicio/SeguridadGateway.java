package com.sos.mspacientes.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/**
 * Conversation directa con ms-seguridad por HTTP local (localhost:8081).
 * Se reenvia el JWT del profesional para que la teleconsulta se asocie
 * a quien realmente dio el alta.
 */
@Component
public class SeguridadGateway {

    private static final Logger log = LoggerFactory.getLogger(SeguridadGateway.class);

    private final RestClient restClient;

    public SeguridadGateway(@Value("${app.seguridad.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * Pide la cita de teleconsulta: ms-seguridad genera fecha/enlace y
     * envia la confirmacion al profesional autenticado.
     */
    public TeleconsultaInfo programarTeleconsulta(String nombrePaciente, String authorization) {
        try {
            CitaResponse respuesta = restClient.post()
                    .uri("/auth/programar-teleconsulta")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> TriageGateway.encabezadoAutorizacion(h, authorization))
                    .body(Map.of("nombrePaciente", nombrePaciente))
                    .retrieve()
                    .body(CitaResponse.class);

            if (respuesta == null || respuesta.enlace() == null) {
                throw new SeguridadNoDisponibleException("ms-seguridad no devolvio la cita de teleconsulta");
            }
            return new TeleconsultaInfo(respuesta.fechaHora(), respuesta.enlace(),
                    respuesta.fechaHoraInicio(), respuesta.fechaHoraFin());
        } catch (HttpClientErrorException e) {
            String detalle = e.getResponseBodyAsString();
            log.warn("ms-seguridad rechazo programar la teleconsulta: {}", detalle);
            throw new SeguridadNoDisponibleException(
                    "No se pudo programar la teleconsulta: " + (detalle.isBlank() ? e.getStatusText() : detalle), e);
        } catch (RestClientException e) {
            log.error("ms-seguridad no respondio al programar la teleconsulta", e);
            throw new SeguridadNoDisponibleException(
                    "ms-seguridad no esta disponible para programar la teleconsulta", e);
        }
    }

    public record TeleconsultaInfo(String fechaHora, String enlace,
                                   String inicioIso, String finIso) {}

    private record CitaResponse(String fechaHora, String enlace,
                                String fechaHoraInicio, String fechaHoraFin) {}
}
