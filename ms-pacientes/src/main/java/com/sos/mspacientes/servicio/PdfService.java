package com.sos.mspacientes.servicio;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

/**
 * Genera PDF a partir de una plantilla Thymeleaf con la libreria
 * OpenHTMLtoPDF (licencia Apache 2.0). Las tipografias DejaVu vienen
 * embebidas en resources/fonts, por eso los acentos, la ñ y los
 * simbolos se ven bien en cualquier sistema.
 */
@Service
public class PdfService {

    private final TemplateEngine templateEngine;

    public PdfService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] generarDesdePlantilla(String plantilla, Map<String, Object> modelo) {
        Context contexto = new Context(Locale.forLanguageTag("es-CO"));
        contexto.setVariables(modelo);
        String html = templateEngine.process(plantilla, contexto);

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registrarTipografias(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(salida);
            builder.run();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new PdfNoGeneradoException("No se pudo generar el PDF: " + e.getMessage(), e);
        }
    }

    private void registrarTipografias(PdfRendererBuilder builder) throws Exception {
        builder.useFont(cargarFuente("fonts/DejaVuSans.ttf"), "SosSans", 400,
                BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(cargarFuente("fonts/DejaVuSans-Bold.ttf"), "SosSans", 700,
                BaseRendererBuilder.FontStyle.NORMAL, true);
    }

    private static FSSupplier<InputStream> cargarFuente(String ruta) throws Exception {
        byte[] datos;
        try (InputStream entrada = new ClassPathResource(ruta).getInputStream()) {
            datos = entrada.readAllBytes();
        }
        return () -> new ByteArrayInputStream(datos);
    }
}
