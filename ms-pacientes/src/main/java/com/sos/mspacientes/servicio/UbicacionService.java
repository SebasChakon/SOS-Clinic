package com.sos.mspacientes.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Geocodificacion con Nominatim (OpenStreetMap): convierte una direccion
 * escrita por el paciente en coordenadas y, como respaldo de Overpass,
 * busca farmacias por categoria cerca de un punto. Sin API key; solo se
 * pide respeto al User-Agent y a la politica de uso razonable de OSM.
 */
@Service
public class UbicacionService {

    private static final Logger log = LoggerFactory.getLogger(UbicacionService.class);
    private static final String USER_AGENT = "SOS-Clinic-Prototipo-Academico";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String urlBusqueda;

    public UbicacionService(@Value("${app.farmacias.nominatim-url}") String urlBusqueda) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder().baseUrl(urlBusqueda).requestFactory(factory).build();
        this.objectMapper = new ObjectMapper();
        this.urlBusqueda = urlBusqueda;
    }

    public record Ubicacion(double lat, double lon, String direccionFormateada) {}

    /** Devuelve la mejor coincidencia o vacio; nunca lanza excepciones. */
    public Optional<Ubicacion> geocodificar(String direccion) {
        if (direccion == null || direccion.isBlank()) return Optional.empty();
        try {
            String respuesta = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .queryParam("q", direccion)
                            .build())
                    .header("User-Agent", USER_AGENT)
                    .retrieve()
                    .body(String.class);

            JsonNode resultados = objectMapper.readTree(respuesta);
            if (resultados == null || !resultados.isArray() || resultados.isEmpty()) {
                return Optional.empty();
            }
            JsonNode mejor = resultados.get(0);
            double lat = mejor.path("lat").asDouble();
            double lon = mejor.path("lon").asDouble();
            String formateada = mejor.path("display_name").asText(direccion);
            return Optional.of(new Ubicacion(lat, lon, formateada));
        } catch (Exception e) {
            log.warn("No se pudo geocodificar la direccion '{}': {}", direccion, e.getMessage());
            return Optional.empty();
        }
    }

    public record PuntoCercano(String nombre, String direccion, double lat, double lon) {}

    /**
     * Farmacias por categoria cerca de un punto, usando la busqueda de
     * Nominatim acotada a un viewbox (bounding box) alrededor del punto.
     * Es el respaldo cuando las instancias de Overpass no responden.
     * Nunca lanza excepciones: si algo falla, devuelve la lista vacia.
     */
    public List<PuntoCercano> buscarFarmaciasCercanas(double lat, double lon, int radioMetros) {
        try {
            double deltaLat = radioMetros / 111000.0;
            double deltaLon = radioMetros / (111000.0 * Math.max(0.01, Math.cos(Math.toRadians(lat))));
            String viewbox = (lon - deltaLon) + "," + (lat + deltaLat) + ","
                    + (lon + deltaLon) + "," + (lat - deltaLat);

            String respuesta = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 10)
                            .queryParam("bounded", 1)
                            .queryParam("viewbox", viewbox)
                            .queryParam("q", "pharmacy")
                            .build())
                    .header("User-Agent", USER_AGENT)
                    .retrieve()
                    .body(String.class);

            JsonNode resultados = objectMapper.readTree(respuesta);
            List<PuntoCercano> lista = new ArrayList<>();
            if (resultados != null && resultados.isArray()) {
                for (JsonNode r : resultados) {
                    // display_name: "Nombre, Calle, Barrio, Ciudad, ..."
                    String[] partes = r.path("display_name").asText("").split(",\\s*");
                    String nombre = partes[0].isBlank() ? "Farmacia" : partes[0];
                    String direccion = partes.length > 2 ? partes[1] + ", " + partes[2]
                            : (partes.length > 1 ? partes[1] : "Sin dirección registrada");
                    double fLat = r.path("lat").asDouble();
                    double fLon = r.path("lon").asDouble();
                    if (fLat == 0 || fLon == 0) continue;
                    lista.add(new PuntoCercano(nombre, direccion, fLat, fLon));
                }
            }
            return lista;
        } catch (Exception e) {
            log.warn("Nominatim no devolvio farmacias cercanas: {}", e.getMessage());
            return List.of();
        }
    }
}
