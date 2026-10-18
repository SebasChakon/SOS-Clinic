package com.sos.mspacientes.servicio;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida que todas las plantillas de correo y la del PDF existen, compilan
 * y renderizan con datos de ejemplo. No usa contexto Spring ni base de datos,
 * asi que corre en cualquier maquina sin levantar nada.
 */
class PlantillasYPdfTest {

    private TemplateEngine motor() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        TemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private String procesar(TemplateEngine engine, String plantilla, Map<String, Object> modelo) {
        Context contexto = new Context(Locale.forLanguageTag("es-CO"));
        contexto.setVariables(modelo);
        return engine.process(plantilla, contexto);
    }

    /**
     * Thymeleaf deja los atributos sin procesar si algo falla. Se busca " th:xxx="
     * y no el literal "th:", porque palabras como "width:" contienen "th:".
     */
    private static boolean sinAtributosThPendientes(String html) {
        return !Pattern.compile("\\sth:[a-z]+=").matcher(html).find();
    }

    private Map<String, Object> modeloHistoria() {
        Map<String, Object> toma = new LinkedHashMap<>();
        toma.put("fecha", "18 de octubre de 2026, 10:30 a. m.");
        toma.put("fc", 82);
        toma.put("presion", "120/80");
        toma.put("temperatura", 36.7);
        toma.put("saturacion", 97);
        toma.put("frecRespiratoria", 16);
        toma.put("glucemia", 95);
        toma.put("sintomas", "Dolor abdominal leve");

        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombrePaciente", "María Ñandú Péña");
        modelo.put("cedula", "1023456789");
        modelo.put("fechaNacimiento", "18 de marzo de 1990");
        modelo.put("categoria", "Verde");
        modelo.put("prioridad", 4);
        modelo.put("ubicacion", "Consultorio 2");
        modelo.put("fechaLlegada", "18 de octubre de 2026, 09:15 a. m.");
        modelo.put("fechaAlta", "18 de octubre de 2026, 11:00 a. m.");
        modelo.put("diagnostico", "Gastroenteritis aguda, sin signos de alarma");
        modelo.put("tratamiento", "Hidratación oral, dieta blanda 48 h. Volver si hay fiebre alta.");
        modelo.put("tomas", List.of(toma));
        modelo.put("destinatario", "paciente@correo.com");
        modelo.put("nombrePdf", "historia_clinica_1023456789_2026-10-18.pdf");
        modelo.put("tieneFarmacias", false);
        return modelo;
    }

    @Test
    void renderizaCitaDeTeleconsultaDelPaciente() {
        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombrePaciente", "María Ñandú");
        modelo.put("fechaHora", "domingo 25 de octubre de 2026 a las 10:00 (hora Colombia)");
        modelo.put("enlace", "https://meet.jit.si/1023456789SOS");
        modelo.put("destinatario", "paciente@correo.com");

        String html = procesar(motor(), "plantillas/cita-teleconsulta-paciente", modelo);

        assertTrue(html.contains("María Ñandú"), "Debe incluir el nombre del paciente");
        assertTrue(html.contains("https://meet.jit.si/1023456789SOS"), "Debe incluir el enlace");
        assertTrue(sinAtributosThPendientes(html), "No deben quedar atributos sin procesar de Thymeleaf");
    }

    @Test
    void renderizaHistoriaClinicaConYSinFarmacias() {
        TemplateEngine engine = motor();

        Map<String, Object> conFarmacias = modeloHistoria();
        conFarmacias.put("tieneFarmacias", true);
        conFarmacias.put("tituloFarmacias", "Farmacias cercanas a tu dirección");
        conFarmacias.put("mapaCid", "cid:mapa-farmacias");
        conFarmacias.put("farmacias", List.of(Map.of(
                "nombre", "Farmacia La Salud",
                "direccion", "Calle 10 # 45-67",
                "distancia", "350 m",
                "enlace", "https://www.google.com/maps/dir/?api=1&destination=4.6,-74.1")));

        String htmlCon = procesar(engine, "plantillas/historia-clinica-resumen", conFarmacias);
        assertTrue(htmlCon.contains("Farmacia La Salud"));
        assertTrue(htmlCon.contains("Farmacias cercanas a tu dirección"));
        assertTrue(htmlCon.contains("cid:mapa-farmacias"));
        assertTrue(htmlCon.contains("Gastroenteritis aguda"));

        // Respaldo: sin direccion resuelta el titulo anuncia farmacias de Bogota.
        Map<String, Object> respaldoBogota = modeloHistoria();
        respaldoBogota.put("tieneFarmacias", true);
        respaldoBogota.put("tituloFarmacias", "Farmacias en Bogotá");
        respaldoBogota.put("farmacias", List.of(Map.of(
                "nombre", "Farmacia Bogotá Centro",
                "direccion", "Carrera 7 # 12-34",
                "distancia", "1,2 km",
                "enlace", "https://www.google.com/maps/dir/?api=1&destination=4.7,-74.07")));
        String htmlBogota = procesar(engine, "plantillas/historia-clinica-resumen", respaldoBogota);
        assertTrue(htmlBogota.contains("Farmacias en Bogotá"));
        assertFalse(htmlBogota.contains("cid:mapa-farmacias"), "El respaldo sin mapa no debe dejar la imagen embebida");

        String htmlSin = procesar(engine, "plantillas/historia-clinica-resumen", modeloHistoria());
        assertTrue(htmlSin.contains("Buscar farmacias en el mapa"), "Sin lista debe verse el buscador de respaldo");
        assertFalse(htmlSin.contains("cid:mapa-farmacias"), "Sin mapa no debe quedar la imagen embebida");
    }

    @Test
    void renderizaDocumentoDeTextoDeCompatibilidad() {
        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("titulo", "Tu historia clínica - SOS");
        modelo.put("contenido", "Contenido de prueba con ñ y acentos: día, número.");
        modelo.put("nota", "Documento enviado por el equipo.");
        modelo.put("destinatario", "paciente@correo.com");

        String html = procesar(motor(), "plantillas/documento-texto", modelo);
        assertTrue(html.contains("día, número"));
        assertTrue(sinAtributosThPendientes(html));
    }

    @Test
    void generaElPdfDeLaHistoriaClinica() {
        byte[] pdf = new PdfService(motor()).generarDesdePlantilla("plantillas/historia-clinica-pdf", modeloHistoria());

        assertNotNull(pdf);
        assertTrue(pdf.length > 5000, "El PDF deberia pesar mas de unos pocos bytes, pesa " + pdf.length);
        assertEquals("%PDF", new String(pdf, 0, 4), "El archivo debe empezar por %PDF");
    }

    @Test
    void generaArchivoDeCalendarioIcs() {
        String ics = new IcsService().construirEvento(
                "Teleconsulta · SOS Clinic",
                "Teleconsulta para María Ñandú, línea nueva\ny coma, punto y coma;",
                "Videollamada en línea · SOS Clinic",
                java.time.ZonedDateTime.of(2026, 10, 25, 10, 0, 0, 0, IcsService.ZONA_COLOMBIA),
                java.time.ZonedDateTime.of(2026, 10, 25, 10, 30, 0, 0, IcsService.ZONA_COLOMBIA),
                "https://meet.jit.si/123SOS");

        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("DTSTART:20261025T150000Z"), "10:00 America/Bogota son 15:00 UTC");
        assertTrue(ics.contains("DTEND:20261025T153000Z"));
        assertTrue(ics.contains("línea nueva\\ny coma"), "Los saltos de linea deben escaparse como \\n");
        assertTrue(ics.contains("punto y coma\\;"), "El punto y coma debe escaparse");
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
    }
}
