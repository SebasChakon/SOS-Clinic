package com.sos.msseguridad.servicio;

import com.sos.msseguridad.modelo.Personal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Genera la cita de teleconsulta (fecha, duracion y sala Jitsi) y envia
 * la confirmacion al profesional por correo, con plantilla HTML y archivo
 * de calendario adjunto.
 */
@Service
public class TeleconsultaService {

    private static final Logger log = LoggerFactory.getLogger(TeleconsultaService.class);
    private static final ZoneId ZONA = IcsService.ZONA_COLOMBIA;
    private static final DateTimeFormatter FECHA_LEGIBLE =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy 'a las' HH:mm",
                    Locale.forLanguageTag("es-CO"));

    private final EmailService emailService;
    private final IcsService icsService;

    public TeleconsultaService(EmailService emailService, IcsService icsService) {
        this.emailService = emailService;
        this.icsService = icsService;
    }

    /** Cita deterministica: +7 dias, 10:00 AM hora Colombia, 30 minutos. */
    public CitaTeleconsulta generarCita(String cedulaProfesional) {
        ZonedDateTime inicio = LocalDate.now(ZONA).plusDays(7).atTime(10, 0).atZone(ZONA);
        ZonedDateTime fin = inicio.plusMinutes(30);
        String documento = cedulaProfesional.replaceAll("[^A-Za-z0-9_-]", "");
        String enlace = "https://meet.jit.si/" + documento + "SOS";
        String fechaHora = capitalizar(inicio.format(FECHA_LEGIBLE)) + " (hora Colombia)";
        return new CitaTeleconsulta(fechaHora, enlace, inicio, fin);
    }

    /** Correo de confirmacion al profesional con el .ics adjunto. */
    public void confirmarPorCorreo(Personal personal, String nombrePaciente, CitaTeleconsulta cita) {
        String nombreProfesional = personal.getNombre() + " " + personal.getApellido();

        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombreProfesional", nombreProfesional);
        modelo.put("nombrePaciente", nombrePaciente);
        modelo.put("fechaHora", cita.fechaHora());
        modelo.put("enlace", cita.enlace());
        modelo.put("destinatario", personal.getEmail());
        String html = emailService.renderizar("plantillas/confirmacion-teleconsulta-profesional", modelo);

        List<EmailService.Adjunto> adjuntos = List.of(new EmailService.Adjunto(
                "teleconsulta_sos.ics",
                icsService.bytes(icsService.construirEvento(
                        "Teleconsulta · SOS Clinic",
                        "Teleconsulta de seguimiento para " + nombrePaciente + ".",
                        "Videollamada en línea · SOS Clinic",
                        cita.inicio(), cita.fin(), cita.enlace())),
                "text/calendar"));

        emailService.enviarConAdjuntos(personal.getEmail(), "Teleconsulta confirmada · SOS Clinic",
                html, adjuntos, List.of());
    }

    public record CitaTeleconsulta(String fechaHora, String enlace,
                                   ZonedDateTime inicio, ZonedDateTime fin) {}

    private static String capitalizar(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        return texto.substring(0, 1).toUpperCase(Locale.forLanguageTag("es-CO")) + texto.substring(1);
    }
}
