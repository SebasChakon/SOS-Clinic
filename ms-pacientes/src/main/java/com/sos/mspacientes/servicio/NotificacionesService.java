package com.sos.mspacientes.servicio;

import com.sos.mspacientes.modelo.Paciente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construye y envia los correos del paciente con las plantillas nuevas:
 * cita de teleconsulta (con archivo de calendario .ics) e historia clinica
 * (con el PDF oficial adjunto). Es el punto unico de notificaciones para
 * que el endpoint heredado y el orquestador del alta se comporten igual.
 */
@Service
public class NotificacionesService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesService.class);

    private final EmailService emailService;
    private final HistoriaClinicaService historiaClinicaService;
    private final IcsService icsService;
    private final FarmaciasService farmaciasService;

    public NotificacionesService(EmailService emailService,
                                 HistoriaClinicaService historiaClinicaService,
                                 IcsService icsService,
                                 FarmaciasService farmaciasService) {
        this.emailService = emailService;
        this.historiaClinicaService = historiaClinicaService;
        this.icsService = icsService;
        this.farmaciasService = farmaciasService;
    }

    /** Cita de teleconsulta para el paciente, con .ics si llegaron las fechas ISO. */
    public void enviarCitaPaciente(Paciente paciente, String fechaHora, String enlace,
                                   String fechaHoraInicioIso, String fechaHoraFinIso) {
        String nombre = paciente.getNombre() + " " + paciente.getApellido();

        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombrePaciente", nombre);
        modelo.put("fechaHora", fechaHora);
        modelo.put("enlace", enlace);
        modelo.put("destinatario", paciente.getEmail());

        String html = emailService.renderizar("plantillas/cita-teleconsulta-paciente", modelo);
        List<EmailService.Adjunto> adjuntos = construirIcs(
                "Teleconsulta · SOS Clinic",
                "Teleconsulta de seguimiento programada para " + fechaHora + ".",
                enlace, fechaHoraInicioIso, fechaHoraFinIso);

        emailService.enviarConAdjuntos(paciente.getEmail(), "Tu cita de teleconsulta · SOS Clinic",
                html, adjuntos, List.of());
    }

    /**
     * Historia clinica en PDF + resumen + farmacias cercanas a la
     * direccion del paciente; si falta o no se pudo ubicar, el correo
     * incluye farmacias de Bogota en general. Exige el JWT del profesional
     * porque la historia se consulta en ms-triage.
     */
    public void enviarHistoriaClinica(Paciente paciente, Integer idRegistro, String direccion,
                                      String authorization) {
        // Las farmacias son best-effort: si fallan, el correo sale igual sin esa seccion.
        FarmaciasService.ResultadoFarmacias farmacias = farmaciasService.buscarPorDireccion(direccion);

        HistoriaClinicaService.HistoriaConPdf historia =
                historiaClinicaService.generarHistoriaCompleta(idRegistro, authorization);

        Map<String, Object> modelo = new LinkedHashMap<>(historia.modelo());
        modelo.put("nombrePdf", historia.nombreArchivo());
        modelo.put("destinatario", paciente.getEmail());

        boolean hayFarmacias = !farmacias.farmacias().isEmpty();
        modelo.put("tieneFarmacias", hayFarmacias);
        List<EmailService.AdjuntoEnLinea> imagenes = List.of();

        if (hayFarmacias) {
            modelo.put("tituloFarmacias", farmacias.cercaDeDireccion()
                    ? "Farmacias cercanas a tu dirección"
                    : "Farmacias en Bogotá");
            List<Map<String, String>> filas = farmacias.farmacias().stream()
                    .map(f -> FarmaciasService.filaPlantilla(f, farmacias.latOrigen(), farmacias.lonOrigen()))
                    .toList();
            modelo.put("farmacias", filas);
            if (farmacias.mapa() != null) {
                modelo.put("mapaCid", "cid:mapa-farmacias");
                imagenes = List.of(new EmailService.AdjuntoEnLinea(
                        "mapa-farmacias", farmacias.mapa(), "image/png"));
            }
        }

        String html = emailService.renderizar("plantillas/historia-clinica-resumen", modelo);
        emailService.enviarConAdjuntos(paciente.getEmail(), "Tu historia clínica · SOS Clinic", html,
                List.of(new EmailService.Adjunto(historia.nombreArchivo(), historia.contenidoPdf(), "application/pdf")),
                imagenes);
    }

    /**
     * Archivo .ics para adjuntar. Si no llegaron fechas ISO o algo falla,
     * el correo sale igualmente sin el adjunto de calendario.
     */
    List<EmailService.Adjunto> construirIcs(String titulo, String descripcion, String enlace,
                                            String inicioIso, String finIso) {
        try {
            if (inicioIso == null || inicioIso.isBlank()) {
                return List.of();
            }
            ZonedDateTime inicio = ZonedDateTime.parse(inicioIso);
            ZonedDateTime fin = (finIso == null || finIso.isBlank())
                    ? inicio.plusMinutes(30)
                    : ZonedDateTime.parse(finIso);
            String ics = icsService.construirEvento(titulo, descripcion,
                    "Videollamada en línea · SOS Clinic", inicio, fin, enlace);
            return List.of(new EmailService.Adjunto("teleconsulta_sos.ics", icsService.bytes(ics), "text/calendar"));
        } catch (Exception e) {
            log.warn("No se pudo generar el archivo de calendario de la cita: {}", e.getMessage());
            return List.of();
        }
    }
}
