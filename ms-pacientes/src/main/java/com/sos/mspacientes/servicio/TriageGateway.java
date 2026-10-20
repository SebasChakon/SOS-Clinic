package com.sos.mspacientes.servicio;

import com.sos.mspacientes.dto.RegistroTriageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/**
 * Conversation directa con ms-triage por HTTP local (localhost:8083 por
 * defecto). Se usan timeouts cortos: si triage esta caido, el alta falla
 * rapido y claro en vez de colgar la peticion del usuario.
 */
@Component
public class TriageGateway {

    private static final Logger log = LoggerFactory.getLogger(TriageGateway.class);

    private final RestClient restClient;

    public TriageGateway(@Value("${app.triage.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** Episodio completo (categoria, estado, diagnostico, tratamiento). */
    public RegistroTriageDTO obtenerRegistro(Integer idRegistro, String authorization) {
        try {
            return restClient.get()
                    .uri("/triage/{id}", idRegistro)
                    .headers(h -> encabezadoAutorizacion(h, authorization))
                    .retrieve()
                    .body(RegistroTriageDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new EpisodioNoEncontradoException("No existe el episodio de triage #" + idRegistro);
        } catch (RestClientException e) {
            log.error("ms-triage no respondio al consultar el episodio #{}", idRegistro, e);
            throw new TriageNoDisponibleException(
                    "No se pudo consultar el episodio en ms-triage. Verifica que el servicio este corriendo.", e);
        }
    }

    /**
     * Marca el episodio como Atendido persistiendo diagnostico y tratamiento.
     * La operacion es idempotente en ms-triage: repetirla no cambia fechas
     * ni duplica nada, por eso el reintento de correos puede volver a llamarla.
     */
    public void darDeAlta(Integer idRegistro, String diagnosticoFinal, String tratamiento, String authorization) {
        try {
            restClient.put()
                    .uri("/triage/{id}/atendido", idRegistro)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> encabezadoAutorizacion(h, authorization))
                    .body(Map.of(
                            "diagnosticoFinal", diagnosticoFinal == null ? "" : diagnosticoFinal,
                            "tratamiento", tratamiento == null ? "" : tratamiento))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new EpisodioNoEncontradoException("No existe el episodio de triage #" + idRegistro);
        } catch (HttpClientErrorException e) {
            throw new TriageNoDisponibleException(
                    "ms-triage rechazo el alta del episodio #" + idRegistro + ": " + e.getMessage(), e);
        } catch (RestClientException e) {
            log.error("ms-triage no respondio al dar de alta el episodio #{}", idRegistro, e);
            throw new TriageNoDisponibleException(
                    "No se pudo completar el alta en ms-triage. Verifica que el servicio este corriendo.", e);
        }
    }

    /** Reenvia el JWT del profesional que esta atendiendo, si lo habia. */
    static void encabezadoAutorizacion(HttpHeaders headers, String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
    }
}
