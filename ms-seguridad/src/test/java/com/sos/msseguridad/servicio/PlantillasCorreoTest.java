package com.sos.msseguridad.servicio;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida que las plantillas de correo de ms-seguridad renderizan sin errores.
 * Corre sin contexto Spring ni base de datos.
 */
class PlantillasCorreoTest {

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

    private String procesar(String plantilla, Map<String, Object> modelo) {
        Context contexto = new Context(Locale.forLanguageTag("es-CO"));
        contexto.setVariables(modelo);
        return motor().process(plantilla, contexto);
    }

    /**
     * Thymeleaf deja los atributos sin procesar si algo falla. Se busca " th:xxx="
     * y no el literal "th:", porque palabras como "width:" contienen "th:".
     */
    private static boolean sinAtributosThPendientes(String html) {
        return !Pattern.compile("\\sth:[a-z]+=").matcher(html).find();
    }

    @Test
    void renderizaConfirmacionDeTeleconsultaAlProfesional() {
        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombreProfesional", "Dra. Ana Gómez");
        modelo.put("nombrePaciente", "María Ñandú Péña");
        modelo.put("fechaHora", "domingo 25 de octubre de 2026 a las 10:00 (hora Colombia)");
        modelo.put("enlace", "https://meet.jit.si/1023456789SOS");
        modelo.put("destinatario", "ana.gomez@sosclinic.local");

        String html = procesar("plantillas/confirmacion-teleconsulta-profesional", modelo);

        assertTrue(html.contains("Dra. Ana Gómez"));
        assertTrue(html.contains("María Ñandú Péña"));
        assertTrue(html.contains("https://meet.jit.si/1023456789SOS"));
        assertTrue(sinAtributosThPendientes(html), "No deben quedar atributos sin procesar de Thymeleaf");
    }

    @Test
    void renderizaCodigoDeVerificacion() {
        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombre", "Ana Gómez");
        modelo.put("codigo", "482913");
        modelo.put("destinatario", "ana.gomez@sosclinic.local");

        String html = procesar("plantillas/codigo-verificacion", modelo);

        assertTrue(html.contains("482913"));
        assertTrue(html.contains("5 minutos"));
        assertTrue(sinAtributosThPendientes(html));
    }

    @Test
    void generaArchivoDeCalendarioDelProfesional() {
        String ics = new IcsService().construirEvento(
                "Teleconsulta · SOS Clinic",
                "Teleconsulta de seguimiento para María Ñandú.",
                "Videollamada en línea · SOS Clinic",
                ZonedDateTime.of(2026, 10, 25, 10, 0, 0, 0, IcsService.ZONA_COLOMBIA),
                ZonedDateTime.of(2026, 10, 25, 10, 30, 0, 0, IcsService.ZONA_COLOMBIA),
                "https://meet.jit.si/123SOS");

        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("DTSTART:20261025T150000Z"));
        assertTrue(ics.contains("URL:https://meet.jit.si/123SOS"));
        assertTrue(ics.contains("SUMMARY:Teleconsulta · SOS Clinic"));
    }
}
