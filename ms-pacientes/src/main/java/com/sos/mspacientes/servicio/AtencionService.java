package com.sos.mspacientes.servicio;

import com.sos.mspacientes.dto.FinalizarAtencionRequest;
import com.sos.mspacientes.dto.FinalizarAtencionResponse;
import com.sos.mspacientes.dto.ReenviarCorreosRequest;
import com.sos.mspacientes.dto.RegistroTriageDTO;
import com.sos.mspacientes.dto.ResultadoEnvioCorreo;
import com.sos.mspacientes.modelo.Paciente;
import com.sos.mspacientes.repositorio.PacienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orquestador del alta de un episodio. Secuencia pensada para ser segura:
 *
 *  1. El alta se persiste PRIMERO en ms-triage. Si eso falla, no se envia
 *     ningun correo y la clinica nunca queda a medias.
 *  2. Recien ahi se envian las notificaciones, cada una aislando sus errores:
 *     un fallo de SMTP o de ms-seguridad nunca deshace el alta y siempre
 *     se informa correo por correo para poder reintentar.
 *
 * El endpoint es idempotente: si el episodio ya esta Atendido, no se vuelve
 * a tocar y solo se reenvian las notificaciones.
 */
@Service
public class AtencionService {

    private static final Logger log = LoggerFactory.getLogger(AtencionService.class);

    private final PacienteRepository pacienteRepository;
    private final TriageGateway triageGateway;
    private final SeguridadGateway seguridadGateway;
    private final NotificacionesService notificacionesService;

    public AtencionService(PacienteRepository pacienteRepository,
                           TriageGateway triageGateway,
                           SeguridadGateway seguridadGateway,
                           NotificacionesService notificacionesService) {
        this.pacienteRepository = pacienteRepository;
        this.triageGateway = triageGateway;
        this.seguridadGateway = seguridadGateway;
        this.notificacionesService = notificacionesService;
    }

    /** Alta + notificaciones en una sola operacion. */
    public FinalizarAtencionResponse finalizar(Integer idRegistro, FinalizarAtencionRequest request,
                                               String authorization) {
        triageGateway.darDeAlta(idRegistro, request.getDiagnosticoFinal(), request.getTratamiento(),
                authorization);
        log.info("Episodio #{} dado de alta; procesando notificaciones", idRegistro);
        return enviarNotificaciones(idRegistro, request.getDireccion(), authorization);
    }

    /** Reintento de correos de un episodio ya atendido (el alta no se toca). */
    public FinalizarAtencionResponse reenviarCorreos(Integer idRegistro, ReenviarCorreosRequest request,
                                                     String authorization) {
        RegistroTriageDTO registro = triageGateway.obtenerRegistro(idRegistro);
        if (registro == null || !registro.estaAtendido()) {
            throw new EpisodioNoEncontradoException(
                    "El episodio #" + idRegistro + " no esta dado de alta; primero completa el alta.");
        }
        return enviarNotificaciones(idRegistro, request.getDireccion(), authorization);
    }

    private FinalizarAtencionResponse enviarNotificaciones(Integer idRegistro, String direccion,
                                                           String authorization) {
        List<ResultadoEnvioCorreo> resultados = new ArrayList<>();

        RegistroTriageDTO registro = triageGateway.obtenerRegistro(idRegistro);
        Paciente paciente = pacienteRepository.findById(registro.idPaciente())
                .orElseThrow(() -> new EpisodioNoEncontradoException(
                        "No existe el paciente #" + registro.idPaciente()));
        String nombrePaciente = paciente.getNombre() + " " + paciente.getApellido();

        // 1) Teleconsulta: ms-seguridad genera la cita y avisa al profesional.
        SeguridadGateway.TeleconsultaInfo cita = null;
        try {
            cita = seguridadGateway.programarTeleconsulta(nombrePaciente, authorization);
            resultados.add(new ResultadoEnvioCorreo("teleconsulta-profesional", ResultadoEnvioCorreo.ENVIADO,
                    "El profesional que atendió recibió la confirmación por correo."));
        } catch (Exception e) {
            log.warn("No se pudo programar la teleconsulta del episodio #{}: {}", idRegistro, e.getMessage());
            resultados.add(new ResultadoEnvioCorreo("teleconsulta-profesional", ResultadoEnvioCorreo.FALLIDO,
                    e.getMessage()));
        }

        // 2) Cita de teleconsulta para el paciente.
        if (paciente.getEmail() == null || paciente.getEmail().isBlank()) {
            resultados.add(new ResultadoEnvioCorreo("cita-paciente", ResultadoEnvioCorreo.OMITIDO,
                    "El paciente no tiene correo registrado."));
        } else if (cita == null) {
            resultados.add(new ResultadoEnvioCorreo("cita-paciente", ResultadoEnvioCorreo.OMITIDO,
                    "No se pudo generar la cita porque ms-seguridad no respondió."));
        } else {
            try {
                notificacionesService.enviarCitaPaciente(paciente, cita.fechaHora(), cita.enlace(),
                        cita.inicioIso(), cita.finIso());
                resultados.add(new ResultadoEnvioCorreo("cita-paciente", ResultadoEnvioCorreo.ENVIADO, null));
            } catch (Exception e) {
                log.error("Fallo el correo de cita del paciente #{}: {}", paciente.getIdPaciente(), e.getMessage(), e);
                resultados.add(new ResultadoEnvioCorreo("cita-paciente", ResultadoEnvioCorreo.FALLIDO,
                        e.getMessage()));
            }
        }

        // 3) Historia clinica en PDF (+ farmacias cercanas a la direccion).
        if (paciente.getEmail() == null || paciente.getEmail().isBlank()) {
            resultados.add(new ResultadoEnvioCorreo("historia-pdf", ResultadoEnvioCorreo.OMITIDO,
                    "El paciente no tiene correo registrado."));
        } else {
            try {
                notificacionesService.enviarHistoriaClinica(paciente, idRegistro, direccion);
                resultados.add(new ResultadoEnvioCorreo("historia-pdf", ResultadoEnvioCorreo.ENVIADO, null));
            } catch (Exception e) {
                log.error("Fallo el correo de historia clinica del episodio #{}", idRegistro, e);
                resultados.add(new ResultadoEnvioCorreo("historia-pdf", ResultadoEnvioCorreo.FALLIDO,
                        e.getMessage()));
            }
        }

        return new FinalizarAtencionResponse(true, resultados,
                cita == null ? null : cita.fechaHora(),
                cita == null ? null : cita.enlace());
    }
}
