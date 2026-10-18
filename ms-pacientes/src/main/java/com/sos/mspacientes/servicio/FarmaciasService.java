package com.sos.mspacientes.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Farmacias cercanas usando datos abiertos de OpenStreetMap:
 * Nominatim convierte la direccion del paciente en coordenadas, Overpass
 * busca "amenity=pharmacy" alrededor y MapaEstaticoService pinta el mapa.
 * Si Overpass no responde (instancias publicas caidas o saturadas), se
 * usa la busqueda por categoria del propio Nominatim como respaldo. Si no
 * hay direccion o no se pudo ubicar, se usa el centro de Bogota. Todo es
 * best-effort: si algo falla, el correo sale sin la seccion de farmacias.
 */
@Service
public class FarmaciasService {

    private static final Logger log = LoggerFactory.getLogger(FarmaciasService.class);
    private static final String USER_AGENT = "SOS-Clinic-Prototipo-Academico";
    /** Instancia alternativa de Overpass por si la principal no responde. */
    private static final String OVERPASS_RESPALDO = "https://overpass.kumi.systems/api/interpreter";

    private final RestClient restClientOverpass;
    private final String urlOverpass;
    private final ObjectMapper objectMapper;
    private final UbicacionService ubicacionService;
    private final MapaEstaticoService mapaEstaticoService;
    private final int radioMetros;
    private final int radioCiudad;
    private final int maxResultados;

    public FarmaciasService(@Value("${app.farmacias.overpass-url}") String urlOverpass,
                            UbicacionService ubicacionService,
                            MapaEstaticoService mapaEstaticoService,
                            @Value("${app.farmacias.radio-metros:3000}") int radioMetros,
                            @Value("${app.farmacias.radio-ciudad:12000}") int radioCiudad,
                            @Value("${app.farmacias.max-resultados:5}") int maxResultados) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        // Sin baseUrl: la URL de la instancia se pasa en cada llamada
        // para poder fallar a la alternativa si la principal no responde.
        this.restClientOverpass = RestClient.builder().requestFactory(factory).build();
        this.urlOverpass = urlOverpass;
        this.objectMapper = new ObjectMapper();
        this.ubicacionService = ubicacionService;
        this.mapaEstaticoService = mapaEstaticoService;
        this.radioMetros = radioMetros;
        this.radioCiudad = radioCiudad;
        this.maxResultados = maxResultados;
    }

    /** Centro de Bogota: respaldo cuando no hay direccion o no se pudo ubicar. */
    static final double CENTRO_BOGOTA_LAT = 4.7104;
    static final double CENTRO_BOGOTA_LON = -74.0721;

    public record FarmaciaCercana(String nombre, String direccion, String distancia,
                                  double lat, double lon, double distanciaMetros) {}

    /** Lista de farmacias + mapa PNG (el mapa puede ser null) + punto de origen usado. */
    public record ResultadoFarmacias(List<FarmaciaCercana> farmacias, byte[] mapa,
                                     Double latOrigen, Double lonOrigen,
                                     boolean cercaDeDireccion) {}

    /**
     * Busca farmacias cerca de la direccion escrita por el personal de salud.
     * Si la direccion falta o no se pudo geocodificar, usa el centro de
     * Bogota como respaldo para mostrar farmacias en general.
     */
    public ResultadoFarmacias buscarPorDireccion(String direccion) {
        double lat = CENTRO_BOGOTA_LAT;
        double lon = CENTRO_BOGOTA_LON;
        int radio = radioCiudad;
        boolean cercaDeDireccion = false;

        if (direccion != null && !direccion.isBlank()) {
            var ubicacion = ubicacionService.geocodificar(direccion);
            if (ubicacion.isPresent()) {
                lat = ubicacion.get().lat();
                lon = ubicacion.get().lon();
                radio = radioMetros;
                cercaDeDireccion = true;
            } else {
                log.info("No se pudo ubicar la direccion '{}'; se mostraran farmacias de Bogota", direccion);
            }
        }

        try {
            List<FarmaciaCercana> farmacias = buscarFarmacias(lat, lon, radio);
            if (farmacias.isEmpty()) {
                return new ResultadoFarmacias(List.of(), null, lat, lon, cercaDeDireccion);
            }

            List<MapaEstaticoService.Marcador> marcadores = new ArrayList<>();
            if (cercaDeDireccion) {
                marcadores.add(new MapaEstaticoService.Marcador(lat, lon, "usuario"));
            }
            farmacias.forEach(f -> marcadores.add(new MapaEstaticoService.Marcador(f.lat(), f.lon(), "farmacia")));

            byte[] mapa = mapaEstaticoService.generarMapa(lat, lon, radio, 536, 300, marcadores);
            return new ResultadoFarmacias(farmacias, mapa, lat, lon, cercaDeDireccion);
        } catch (Exception e) {
            log.warn("La busqueda de farmacias fallo (el correo salira sin esa seccion): {}", e.getMessage());
            return new ResultadoFarmacias(List.of(), null, lat, lon, cercaDeDireccion);
        }
    }

    /**
     * Cadena de fuentes: primero Overpass (principal y respaldo kumi) y,
     * si ambas fallan o no devuelven nada, la busqueda por categoria de
     * Nominatim (servidor distinto, casi siempre disponible).
     */
    private List<FarmaciaCercana> buscarFarmacias(double lat, double lon, int radio) {
        try {
            List<FarmaciaCercana> lista = consultarOverpass(lat, lon, radio);
            if (!lista.isEmpty()) {
                return lista;
            }
            log.info("Overpass no devolvio farmacias alrededor de {},{}; se intenta con Nominatim", lat, lon);
        } catch (Exception e) {
            log.warn("Overpass fallo ({}); se intenta con Nominatim", e.getMessage());
        }

        List<FarmaciaCercana> lista = new ArrayList<>();
        for (UbicacionService.PuntoCercano punto : ubicacionService.buscarFarmaciasCercanas(lat, lon, radio)) {
            double distancia = distanciaMetros(lat, lon, punto.lat(), punto.lon());
            lista.add(new FarmaciaCercana(punto.nombre(), punto.direccion(),
                    formatearDistancia(distancia), punto.lat(), punto.lon(), distancia));
        }
        lista.sort(Comparator.comparingDouble(FarmaciaCercana::distanciaMetros));
        return lista.size() > maxResultados ? new ArrayList<>(lista.subList(0, maxResultados)) : lista;
    }

    private List<FarmaciaCercana> consultarOverpass(double lat, double lon, int radio) throws Exception {
        String consulta = "[out:json][timeout:10];"
                + "(node[\"amenity\"=\"pharmacy\"](around:" + radio + "," + lat + "," + lon + ");"
                + "way[\"amenity\"=\"pharmacy\"](around:" + radio + "," + lat + "," + lon + "););"
                + "out center " + (maxResultados * 4) + ";";

        var cuerpo = new LinkedMultiValueMap<String, String>();
        cuerpo.add("data", consulta);

        Exception ultimoError = null;
        for (String instancia : List.of(urlOverpass, OVERPASS_RESPALDO)) {
            try {
                byte[] respuesta = restClientOverpass.post()
                        .uri(instancia)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .body(cuerpo)
                        .retrieve()
                        .body(byte[].class);
                return parsearResultado(descomprimir(respuesta), lat, lon);
            } catch (Exception e) {
                ultimoError = e;
                log.warn("Overpass no respondio en {}: {}", instancia, e.getMessage());
            }
        }
        throw ultimoError;
    }

    /** Overpass puede responder gzip o text/plain; se normaliza a bytes planos. */
    private static byte[] descomprimir(byte[] datos) throws IOException {
        if (datos != null && datos.length >= 2
                && (datos[0] & 0xff) == 0x1f && (datos[1] & 0xff) == 0x8b) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(datos))) {
                return gzip.readAllBytes();
            }
        }
        return datos;
    }

    private List<FarmaciaCercana> parsearResultado(byte[] respuesta, double lat, double lon) throws IOException {
        JsonNode raiz = objectMapper.readTree(respuesta);
        JsonNode elementos = raiz.path("elements");
        List<FarmaciaCercana> lista = new ArrayList<>();

        if (elementos.isArray()) {
            for (JsonNode e : elementos) {
                double fLat = e.has("lat") ? e.path("lat").asDouble() : e.path("center").path("lat").asDouble();
                double fLon = e.has("lon") ? e.path("lon").asDouble() : e.path("center").path("lon").asDouble();
                if (fLat == 0 || fLon == 0) continue;

                String nombre = e.path("tags").path("name").asText("Farmacia");
                String calle = e.path("tags").path("addr:street").asText("");
                String numero = e.path("tags").path("addr:housenumber").asText("");
                String direccion = (!calle.isBlank())
                        ? calle + (numero.isBlank() ? "" : " #" + numero)
                        : "Sin dirección registrada";

                double distancia = distanciaMetros(lat, lon, fLat, fLon);
                lista.add(new FarmaciaCercana(nombre, direccion, formatearDistancia(distancia),
                        fLat, fLon, distancia));
            }
        }

        lista.sort(Comparator.comparingDouble(FarmaciaCercana::distanciaMetros));
        return lista.size() > maxResultados ? new ArrayList<>(lista.subList(0, maxResultados)) : lista;
    }

    static double distanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        double radioTierra = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return radioTierra * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static String formatearDistancia(double metros) {
        if (metros < 1000) {
            return Math.round(metros) + " m";
        }
        return String.format("%.1f km", metros / 1000).replace('.', ',');
    }

    /** Enlace de ruta caminando desde la ubicacion del paciente hasta la farmacia. */
    public static String enlaceComoLlegar(double latOrigen, double lonOrigen, FarmaciaCercana farmacia) {
        return "https://www.google.com/maps/dir/?api=1"
                + "&origin=" + latOrigen + "," + lonOrigen
                + "&destination=" + farmacia.lat() + "," + farmacia.lon()
                + "&travelmode=walking";
    }

    /** Modelo de fila para la plantilla del correo. */
    public static Map<String, String> filaPlantilla(FarmaciaCercana f, double latOrigen, double lonOrigen) {
        Map<String, String> fila = new LinkedHashMap<>();
        fila.put("nombre", f.nombre());
        fila.put("direccion", f.direccion());
        fila.put("distancia", f.distancia());
        fila.put("enlace", enlaceComoLlegar(latOrigen, lonOrigen, f));
        return fila;
    }
}
