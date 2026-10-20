/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msiaagente.ms_ia_agente.herramientas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
public class HerramientasSos {

    public record Resultado(String datosParaModelo, List<Map<String, String>> pasos) {}

    private static final Set<String> ACCIONES = Set.of("navegar", "clic", "escribir", "elegir");

    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${app.gateway.url}")
    private String gateway;

    public HerramientasSos(JsonMapper json) { this.json = json; }

    /* ---------- catalogo (formato function calling) ---------- */

    private static Map<String, Object> str(String descripcion) {
        return Map.of("type", "string", "description", descripcion);
    }

    private static Map<String, Object> fn(String nombre, String descripcion, Map<String, Object> propiedades, List<String> requeridos) {
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("type", "object");
        parametros.put("properties", propiedades);
        parametros.put("required", requeridos);

        Map<String, Object> funcion = new LinkedHashMap<>();
        funcion.put("name", nombre);
        funcion.put("description", descripcion);
        funcion.put("parameters", parametros);

        return Map.of("type", "function", "function", funcion);
    }

    public List<Map<String, Object>> catalogo() {
        Map<String, Object> paso = new LinkedHashMap<>();
        paso.put("type", "object");
        paso.put("properties", Map.of(
                "accion", str("navegar | clic | escribir | elegir"),
                "objetivo", str("Referencia eN del elemento, su id o su texto visible. En navegar: la pagina, ej. pacientes.html"),
                "valor", str("Texto a escribir u opcion a elegir")));
        paso.put("required", List.of("accion"));

        Map<String, Object> pasos = new LinkedHashMap<>();
        pasos.put("type", "array");
        pasos.put("description", "Maximo 8 pasos, en orden");
        pasos.put("items", paso);

        return List.of(
                fn("consultar_lista_espera",
                        "Estado actual de la lista de priorizacion de triage: cuantos pacientes hay, por categoria, la mayor espera y los primeros de la fila.",
                        Map.of(), List.of()),
                fn("buscar_paciente",
                        "Busca pacientes registrados por cedula, completa o parcial.",
                        Map.of("q", str("Cedula o los primeros digitos")), List.of("q")),
                fn("actuar_en_pagina",
                        "Maneja la pantalla del usuario: navega, hace clic, escribe o elige opciones.",
                        Map.of("pasos", pasos), List.of("pasos")));
    }

    /* ---------- ejecucion ---------- */

    public Resultado ejecutar(String nombre, Map<String, Object> args, String auth, List<String> paginasPermitidas) {
        return switch (nombre) {
            case "consultar_lista_espera" -> listaEspera(auth);
            case "buscar_paciente" -> buscarPaciente(texto(args.get("q")), auth);
            case "actuar_en_pagina" -> actuar(args, paginasPermitidas);
            default -> new Resultado("Herramienta desconocida.", List.of());
        };
    }

    @SuppressWarnings("unchecked")
    private Resultado listaEspera(String auth) {
        Object crudo = get("/ms-triage/triage/prioridad", auth);
        if (!(crudo instanceof List<?> lista)) return new Resultado("No pude consultar la lista de espera ahora.", List.of());
        if (lista.isEmpty()) return new Resultado("No hay pacientes en la lista en este momento.", List.of());

        Map<String, Integer> porCategoria = new LinkedHashMap<>();
        int mayorEspera = 0;
        String descMayor = "";

        for (Object o : lista) {
            Map<String, Object> r = (Map<String, Object>) o;
            Map<String, Object> cat = (Map<String, Object>) r.get("categoriaTriage");
            String nombreCat = String.valueOf(cat.get("nombreCategoria"));
            porCategoria.merge(nombreCat, 1, Integer::sum);
            int espera = r.get("tiempoEsperaMinutos") instanceof Number n ? n.intValue() : 0;
            if (espera > mayorEspera) { mayorEspera = espera; descMayor = nombreCat + ", " + texto(r.get("ubicacion")); }
        }

        StringBuilder sb = new StringBuilder("Pacientes en lista: " + lista.size() + ". Por categoria: ");
        porCategoria.forEach((k, v) -> sb.append(k).append(" ").append(v).append("; "));
        sb.append("Mayor espera: ").append(mayorEspera).append(" min (").append(descMayor).append("). ");
        // Privacidad: los nombres y cedulas no se envian al proveedor del modelo.
        sb.append("Por privacidad no se incluyen nombres de pacientes; revisa la lista en pantalla.");
        return new Resultado(sb.toString(), List.of());
    }

    @SuppressWarnings("unchecked")
    private Resultado buscarPaciente(String q, String auth) {
        if (q.isBlank()) return new Resultado("Falta la cedula a buscar.", List.of());
        Object crudo = get("/ms-pacientes/pacientes/buscar?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8), auth);
        if (!(crudo instanceof List<?> lista) || lista.isEmpty()) {
            return new Resultado("No hay pacientes registrados con una cedula que contenga " + q + ".", List.of());
        }
        // Privacidad: no se envian nombres ni cedulas completas al modelo.
        return new Resultado("Coincidencias encontradas: " + lista.size()
                + ". Por privacidad, revisa la tabla de pacientes para ver los detalles.", List.of());
    }

    private Resultado actuar(Map<String, Object> args, List<String> paginasPermitidas) {
        if (!(args.get("pasos") instanceof List<?> lista) || lista.isEmpty()) {
            return new Resultado("No recibi pasos para ejecutar.", List.of());
        }

        List<Map<String, String>> pasos = new ArrayList<>();
        for (Object o : lista) {
            if (pasos.size() >= 8) break;
            if (!(o instanceof Map<?, ?> m)) continue;

            String accion = normalizarAccion(texto(m.get("accion")));
            if (!ACCIONES.contains(accion)) continue;

            String objetivo = texto(m.get("objetivo"));
            String valor = texto(m.get("valor"));

            if (accion.equals("navegar")) {
                if (!objetivo.contains(".html")) objetivo = objetivo + ".html";
                String pagina = objetivo.split("\\?")[0];
                // El asistente no puede llevar a pantallas que el perfil del usuario no tiene
                if (paginasPermitidas != null && !paginasPermitidas.contains(pagina)) continue;
            }
            pasos.add(Map.of("accion", accion, "objetivo", objetivo, "valor", valor));
        }

        if (pasos.isEmpty()) {
            return new Resultado("No puedo hacer eso: esa pantalla o accion no esta disponible para tu usuario.", List.of());
        }
        return new Resultado("Listo, ejecuto " + pasos.size() + " paso(s) en la pantalla.", pasos);
    }

    /* ---------- utilidades ---------- */

    private static String normalizarAccion(String a) {
        a = a.toLowerCase();
        return switch (a) {
            case "ir", "abrir", "ir_a", "navegar" -> "navegar";
            case "click", "presionar", "pulsar", "clic" -> "clic";
            case "rellenar", "llenar", "digitar", "teclear", "escribir" -> "escribir";
            case "seleccionar", "filtrar", "elegir" -> "elegir";
            default -> a;
        };
    }

    private static String texto(Object o) { return o == null ? "" : String.valueOf(o).trim(); }

    private Object get(String ruta, String auth) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(gateway + ruta)).timeout(Duration.ofSeconds(10)).GET();
            if (auth != null && !auth.isBlank()) b.header("Authorization", auth);
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return null;
            return json.readValue(r.body(), Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}