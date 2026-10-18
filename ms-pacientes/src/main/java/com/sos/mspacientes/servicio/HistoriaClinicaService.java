package com.sos.mspacientes.servicio;

import com.sos.mspacientes.dto.RegistroTriageDTO;
import com.sos.mspacientes.modelo.EstadoPaciente;
import com.sos.mspacientes.modelo.Paciente;
import com.sos.mspacientes.repositorio.EstadoPacienteRepository;
import com.sos.mspacientes.repositorio.PacienteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Consolida la historia clinica de un episodio (paciente de ms-pacientes,
 * episodio de ms-triage y tomas de signos vitales) y la convierte en PDF
 * o en el modelo del correo de resumen.
 */
@Service
public class HistoriaClinicaService {

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES_CO);
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, hh:mm a", ES_CO);

    private final PacienteRepository pacienteRepository;
    private final EstadoPacienteRepository estadoPacienteRepository;
    private final TriageGateway triageGateway;
    private final PdfService pdfService;

    public HistoriaClinicaService(PacienteRepository pacienteRepository,
                                  EstadoPacienteRepository estadoPacienteRepository,
                                  TriageGateway triageGateway,
                                  PdfService pdfService) {
        this.pacienteRepository = pacienteRepository;
        this.estadoPacienteRepository = estadoPacienteRepository;
        this.triageGateway = triageGateway;
        this.pdfService = pdfService;
    }

    /** Modelo que consume la plantilla del PDF (y el resumen del correo). */
    public Map<String, Object> construirModelo(Integer idRegistro) {
        RegistroTriageDTO registro = triageGateway.obtenerRegistro(idRegistro);
        Paciente paciente = pacienteRepository.findById(registro.idPaciente())
                .orElseThrow(() -> new EpisodioNoEncontradoException(
                        "No existe el paciente #" + registro.idPaciente() + " del episodio #" + idRegistro));

        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombrePaciente", paciente.getNombre() + " " + paciente.getApellido());
        modelo.put("cedula", paciente.getCedula());
        modelo.put("fechaNacimiento", paciente.getFechaNacimiento() == null
                ? "-" : paciente.getFechaNacimiento().format(FECHA_LARGA));
        modelo.put("categoria", registro.categoriaTriage() == null
                ? "-" : registro.categoriaTriage().nombre());
        modelo.put("prioridad", registro.categoriaTriage() == null
                ? null : registro.categoriaTriage().prioridad());
        modelo.put("ubicacion", registro.ubicacion() == null || registro.ubicacion().isBlank()
                ? "Sin asignar" : registro.ubicacion());
        modelo.put("fechaLlegada", registro.fechaHoraLlegada() == null
                ? "-" : registro.fechaHoraLlegada().format(FECHA_HORA));
        modelo.put("fechaAlta", LocalDateTime.now().format(FECHA_HORA));
        modelo.put("diagnostico", registro.diagnosticoFinal() == null ? "-" : registro.diagnosticoFinal());
        modelo.put("tratamiento", registro.tratamiento() == null ? "-" : registro.tratamiento());
        modelo.put("tomas", construirTomas(idRegistro));
        return modelo;
    }

    private List<Map<String, Object>> construirTomas(Integer idRegistro) {
        List<EstadoPaciente> signos = new ArrayList<>(estadoPacienteRepository.findByIdRegistro(idRegistro));
        signos.sort(Comparator.comparing(EstadoPaciente::getFechaHoraToma,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<Map<String, Object>> tomas = new ArrayList<>();
        for (EstadoPaciente s : signos) {
            Map<String, Object> toma = new LinkedHashMap<>();
            toma.put("fecha", s.getFechaHoraToma() == null ? "-" : s.getFechaHoraToma().format(FECHA_HORA));
            toma.put("fc", s.getFrecuenciaCardiaca());
            toma.put("presion", s.getPresionSistolica() + "/" + s.getPresionDiastolica());
            toma.put("temperatura", s.getTemperatura());
            toma.put("saturacion", s.getSaturacion());
            toma.put("frecRespiratoria", s.getFrecuenciaRespiratoria());
            toma.put("glucemia", s.getGlucemia());
            toma.put("sintomas", s.getDescripcionSintoma() == null || s.getDescripcionSintoma().isBlank()
                    ? null : s.getDescripcionSintoma());
            tomas.add(toma);
        }
        return tomas;
    }

    /** PDF completo de la historia clinica del episodio, con su nombre de archivo. */
    public PdfHistoria generarPdf(Integer idRegistro) {
        HistoriaConPdf historia = generarHistoriaCompleta(idRegistro);
        return new PdfHistoria(historia.contenidoPdf(), historia.nombreArchivo());
    }

    /** Modelo + PDF + nombre de archivo con una sola consulta a ms-triage. */
    public HistoriaConPdf generarHistoriaCompleta(Integer idRegistro) {
        Map<String, Object> modelo = construirModelo(idRegistro);
        byte[] contenido = pdfService.generarDesdePlantilla("plantillas/historia-clinica-pdf", modelo);
        String nombre = "historia_clinica_" + modelo.get("cedula") + "_" + LocalDate.now() + ".pdf";
        return new HistoriaConPdf(modelo, contenido, nombre);
    }

    public record PdfHistoria(byte[] contenido, String nombreArchivo) {}

    public record HistoriaConPdf(Map<String, Object> modelo, byte[] contenidoPdf, String nombreArchivo) {}

    public static String formatearFechaCorta(LocalDate fecha) {
        return fecha == null ? "-" : fecha.format(FECHA_LARGA);
    }
}
