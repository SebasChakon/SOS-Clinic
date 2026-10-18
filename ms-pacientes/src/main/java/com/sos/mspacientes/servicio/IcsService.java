package com.sos.mspacientes.servicio;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Archivos .ics (RFC 5545) que se adjuntan a los correos de cita.
 * Gmail y Outlook muestran el boton "agregar al calendario" y convierten
 * las fechas a la zona horaria del lector, por eso se guardan en UTC.
 */
@Service
public class IcsService {

    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private static final DateTimeFormatter FORMATO_ICS =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    public String construirEvento(String titulo, String descripcion, String lugar,
                                  ZonedDateTime inicio, ZonedDateTime fin, String enlace) {
        ZonedDateTime inicioUtc = inicio.withZoneSameInstant(ZoneOffset.UTC);
        ZonedDateTime finUtc = fin.withZoneSameInstant(ZoneOffset.UTC);

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//SOS Clinic//Triage//ES\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");
        ics.append("BEGIN:VEVENT\r\n");
        ics.append("UID:").append(UUID.randomUUID()).append("@sosclinic\r\n");
        ics.append("DTSTAMP:").append(ZonedDateTime.now(ZoneOffset.UTC).format(FORMATO_ICS)).append("\r\n");
        ics.append("DTSTART:").append(inicioUtc.format(FORMATO_ICS)).append("\r\n");
        ics.append("DTEND:").append(finUtc.format(FORMATO_ICS)).append("\r\n");
        ics.append("SUMMARY:").append(escapar(titulo)).append("\r\n");
        ics.append("DESCRIPTION:").append(escapar(descripcion)).append("\r\n");
        ics.append("LOCATION:").append(escapar(lugar)).append("\r\n");
        if (enlace != null && !enlace.isBlank()) {
            ics.append("URL:").append(enlace).append("\r\n");
        }
        ics.append("STATUS:CONFIRMED\r\n");
        ics.append("TRANSP:OPAQUE\r\n");
        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR\r\n");
        return ics.toString();
    }

    public byte[] bytes(String ics) {
        return ics.getBytes(StandardCharsets.UTF_8);
    }

    /** Las lineas del .ics van separadas por CRLF y los textos escapados. */
    private static String escapar(String texto) {
        if (texto == null) return "";
        return texto
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }
}
