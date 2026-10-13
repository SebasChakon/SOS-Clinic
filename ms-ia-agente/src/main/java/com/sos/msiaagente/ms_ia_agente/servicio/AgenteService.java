/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msiaagente.ms_ia_agente.servicio;

import com.sos.msiaagente.ms_ia_agente.groq.GroqCliente;
import com.sos.msiaagente.ms_ia_agente.herramientas.HerramientasSos;
import com.sos.msiaagente.ms_ia_agente.herramientas.HerramientasSos.Resultado;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AgenteService {

    private static final int MAX_RONDAS = 4;
    private static final Pattern VERBOS = Pattern.compile(
            "\\b(ve|ir|abre|abrir|lleva|llena|llenar|rellen|escrib|registr|busc|selecciona|elige|elegir|pon|poner|clic|click|presiona|pulsa|cambia|crea|agrega)");

    public record Contexto(String pagina, String usuario, String perfil,
                           List<String> paginasPermitidas, List<String> elementos) {}
    public record Pregunta(String mensaje, List<Map<String, String>> historial, Contexto contexto) {}
    public record Respuesta(String texto, List<Map<String, String>> pasos, int tokens) {}

    private final GroqCliente groq;
    private final HerramientasSos herramientas;
    private final JsonMapper json;

    public AgenteService(GroqCliente groq, HerramientasSos herramientas, JsonMapper json) {
        this.groq = groq;
        this.herramientas = herramientas;
        this.json = json;
    }

    @SuppressWarnings("unchecked")
    public Respuesta responder(Pregunta p, String auth) {
        String mensaje = recortar(p.mensaje(), 600);
        Contexto ctx = p.contexto();

        List<Map<String, Object>> mensajes = new ArrayList<>();
        mensajes.add(Map.of("role", "system", "content", prompt(ctx, mensaje)));

        if (p.historial() != null) {
            List<Map<String, String>> h = p.historial();
            for (Map<String, String> t : h.subList(Math.max(0, h.size() - 4), h.size())) {
                String rol = "assistant".equals(t.get("role")) ? "assistant" : "user";
                mensajes.add(Map.of("role", rol, "content", recortar(t.getOrDefault("content", ""), 280)));
            }
        }
        mensajes.add(Map.of("role", "user", "content", mensaje));

        List<Map<String, String>> pasos = new ArrayList<>();
        List<Map<String, Object>> catalogo = herramientas.catalogo();
        List<String> permitidas = ctx == null ? null : ctx.paginasPermitidas();
        int tokens = 0;

        for (int ronda = 1; ronda <= MAX_RONDAS; ronda++) {
            boolean ultima = ronda == MAX_RONDAS; // en la ultima ronda no hay herramientas: debe contestar
            Map<String, Object> resp = groq.chat(mensajes, ultima ? null : catalogo);
            tokens += totalTokens(resp);

            Map<String, Object> msg = primerMensaje(resp);
            List<Map<String, Object>> llamadas = (List<Map<String, Object>>) msg.get("tool_calls");

            if (llamadas == null || llamadas.isEmpty()) {
                String texto = msg.get("content") == null ? "" : msg.get("content").toString().trim();
                return new Respuesta(texto.isEmpty() ? "No logré entender tu pedido. ¿Puedes reformularlo?" : texto, pasos, tokens);
            }

            Map<String, Object> eco = new LinkedHashMap<>();
            eco.put("role", "assistant");
            eco.put("content", msg.get("content"));
            eco.put("tool_calls", llamadas);
            mensajes.add(eco);

            boolean soloActuar = true;
            String textoTerminal = "";

            for (Map<String, Object> llamada : llamadas) {
                Map<String, Object> f = (Map<String, Object>) llamada.get("function");
                String nombre = String.valueOf(f.get("name"));
                Resultado r = herramientas.ejecutar(nombre, parsearArgs(f.get("arguments")), auth, permitidas);
                pasos.addAll(r.pasos());

                if ("actuar_en_pagina".equals(nombre)) textoTerminal = r.datosParaModelo();
                else soloActuar = false;

                mensajes.add(Map.of("role", "tool",
                        "tool_call_id", String.valueOf(llamada.get("id")),
                        "content", recortar(r.datosParaModelo(), 2000)));
            }
            // actuar_en_pagina es terminal: su texto es la respuesta, sin otra ronda
            if (soloActuar) return new Respuesta(textoTerminal, pasos, tokens);
        }
        return new Respuesta("No pude completar la consulta. Intenta de nuevo.", pasos, tokens);
    }

    private String prompt(Contexto c, String mensaje) {
        String pagina = c == null ? "" : c.pagina();
        String usuario = c == null ? "" : c.usuario();
        String perfil = c == null ? "" : c.perfil();
        String permitidas = c == null || c.paginasPermitidas() == null ? "" : String.join(", ", c.paginasPermitidas());

        String base = """
                Eres el asistente de SOS Clinic, un sistema de triage hospitalario. Ayudas al personal a usar la aplicacion, consultas datos reales con funciones y puedes manejar la pantalla.

                REGLAS
                - Todo dato (pacientes, lista de espera) sale de una funcion. Si no lo trae, di que no lo tienes. Nunca inventes cifras ni nombres.
                - Para explicar como se usa la aplicacion responde con lo que sabes de las pantallas, sin llamar funciones.
                - Para mover la pantalla usa actuar_en_pagina con pasos concretos. Solo puedes navegar a las paginas permitidas del usuario; si pide algo que su perfil no puede hacer, explicalo.
                - NUNCA des de alta, elimines ni desactives: eso lo hace la persona. Puedes dejar los campos listos para que ella confirme.
                - Las referencias eN solo valen para la pagina actual. Tras navegar, usa los ids de abajo.
                - Fechas en formato AAAA-MM-DD.
                - Responde en espanol, maximo 3 frases, sin Markdown, como un companero de trabajo.
                - Si piden algo ajeno al sistema, di brevemente que solo ayudas con SOS Clinic.
                - Cedulas y telefonos: solo digitos, sin puntos ni espacios (el usuario puede dictarlos por voz).
                - Si el pedido viene de voz puede traer errores de transcripcion; si algo no cuadra, pide confirmar en vez de adivinar.

                PANTALLAS (id de sus campos)
                - pacientes.html (recepcion): registrar paciente (nombre, apellido, cedula, fechaNacimiento, email) y buscar por cedula (buscarCedula). Boton enviar: btnRegistrar.
                - triage-nuevo.html (doctor de triage): registrar llegada a triage. Campos: cedula (busca pacientes al escribir), categoria (1 Resucitacion mas urgente ... 5 Sin urgencia), ubicacion (consultorios 1-5 y salas). Boton: btnRegistrar.
                - lista-espera.html: lista de pacientes en espera por urgencia; "Ver" abre el seguimiento.
                - seguimiento.html: signos vitales (frecuenciaCardiaca, frecuenciaRespiratoria, presionSistolica, presionDiastolica, temperatura, saturacion, glucemia, descripcionSintoma), cambiar ubicacion, marcar en atencion y dar de alta (exige diagnosticoFinal y tratamiento).
                - auditoria.html: registro de acciones del sistema.
                - roles.html: administracion de perfiles, actividades, usuarios y turnos.

                CONTEXTO
                Pagina actual: %s | Usuario: %s | Perfil: %s
                Paginas permitidas: %s
                """.formatted(pagina, usuario, perfil, permitidas);

        // Los elementos de la pantalla solo se envian si el pedido parece una accion (ahorra tokens)
        if (c != null && c.elementos() != null && !c.elementos().isEmpty() && VERBOS.matcher(norm(mensaje)).find()) {
            base += "\nELEMENTOS VISIBLES AHORA:\n" + recortar(String.join("\n", c.elementos()), 1600);
        }
        return base;
    }

    private Map<String, Object> parsearArgs(Object crudo) {
        try {
            if (crudo instanceof Map<?, ?> m) return (Map<String, Object>) m;
            return json.readValue(String.valueOf(crudo), Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> primerMensaje(Map<String, Object> resp) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        return (Map<String, Object>) choices.get(0).get("message");
    }

    @SuppressWarnings("unchecked")
    private static int totalTokens(Map<String, Object> resp) {
        Object u = resp.get("usage");
        return (u instanceof Map<?, ?> m && m.get("total_tokens") instanceof Number n) ? n.intValue() : 0;
    }

    private static String recortar(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }
}
