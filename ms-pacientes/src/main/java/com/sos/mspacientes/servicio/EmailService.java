package com.sos.mspacientes.servicio;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Envio de correos con plantillas HTML (Thymeleaf), texto alternativo,
 * adjuntos descargables e imagenes embebidas (cid:).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.remitente}")
    private String remitente;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    /** Archivo que viaja adjunto y el usuario descarga. */
    public record Adjunto(String nombre, byte[] contenido, String tipoContenido) {}

    /** Imagen embebida en el HTML; en la plantilla se referencia como th:src="${...}" con valor "cid:xyz". */
    public record AdjuntoEnLinea(String cid, byte[] contenido, String tipoContenido) {}

    /**
     * Renderiza una plantilla Thymeleaf ubicada en src/main/resources/templates/.
     * Ejemplo: renderizar("plantillas/cita-teleconsulta-paciente", Map.of(...)).
     * El resolutor por defecto agrega la extension .html, por eso el nombre
     * se pasa sin ella.
     */
    public String renderizar(String plantilla, Map<String, Object> modelo) {
        Context contexto = new Context(Locale.forLanguageTag("es-CO"));
        contexto.setVariables(modelo);
        return templateEngine.process(plantilla, contexto);
    }

    /** Envia un correo HTML sencillo (sin adjuntos). */
    public void enviarHtml(String destinatario, String asunto, String html) {
        enviarConAdjuntos(destinatario, asunto, html, List.of(), List.of());
    }

    /**
     * Envia un correo HTML con texto alternativo automatico, adjuntos
     * descargables y imagenes embebidas. Lanza {@link EmailNoEnviadoException}
     * si el servidor SMTP no acepta el mensaje.
     */
    public void enviarConAdjuntos(String destinatario, String asunto, String html,
                                  List<Adjunto> adjuntos, List<AdjuntoEnLinea> enLinea) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mensaje, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(aTextoPlano(html), html);

            if (enLinea != null) {
                for (AdjuntoEnLinea a : enLinea) {
                    helper.addInline(a.cid(), new ByteArrayDataSource(a.contenido(), a.tipoContenido()));
                }
            }
            if (adjuntos != null) {
                for (Adjunto a : adjuntos) {
                    helper.addAttachment(a.nombre(), new ByteArrayDataSource(a.contenido(), a.tipoContenido()));
                }
            }
            mailSender.send(mensaje);
            log.info("Correo [{}] enviado a {}", asunto, destinatario);
        } catch (MessagingException e) {
            throw new EmailNoEnviadoException(
                    "No se pudo preparar el correo para " + destinatario + ": " + e.getMessage(), e);
        }
    }

    /**
     * Version en texto plano del HTML: los clientes de correo muy antiguos y
     * algunos lectores de pantalla la prefieren; ademas queda en el historial
     * del mensaje aunque el destinatario tenga las imagenes desactivadas.
     */
    private static String aTextoPlano(String html) {
        if (html == null) return "";
        return html
                .replaceAll("(?is)<(script|style).*?</\\1>", " ")
                .replaceAll("(?s)<br\\s*/?>", "\n")
                .replaceAll("(?s)</(p|div|tr|li|h[1-6])>", "\n")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
