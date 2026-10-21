/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.controlador;

import com.sos.msseguridad.dto.ClaveTemporalResponse;
import com.sos.msseguridad.dto.PersonalRequest;
import com.sos.msseguridad.dto.PersonalResponse;
import com.sos.msseguridad.dto.RechazarRequest;
import com.sos.msseguridad.modelo.Perfil;
import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.modelo.Turno;
import com.sos.msseguridad.repositorio.PerfilRepository;
import com.sos.msseguridad.repositorio.PersonalRepository;
import com.sos.msseguridad.repositorio.TurnoRepository;
import com.sos.msseguridad.seguridad.PoliticaClaves;
import com.sos.msseguridad.seguridad.RecuperacionClaveService;
import com.sos.msseguridad.servicio.AuditoriaService;
import com.sos.msseguridad.servicio.EmailService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/personal")
public class PersonalAdminController {

    private static final Logger log = LoggerFactory.getLogger(PersonalAdminController.class);
    private static final String CARACTERES_TEMPORAL =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!?#$%.*@_+-";

    private final PersonalRepository personalRepository;
    private final PerfilRepository perfilRepository;
    private final TurnoRepository turnoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final RecuperacionClaveService recuperacionClaveService;
    private final PoliticaClaves politicaClaves;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public PersonalAdminController(PersonalRepository personalRepository,
                                   PerfilRepository perfilRepository,
                                   TurnoRepository turnoRepository,
                                   PasswordEncoder passwordEncoder,
                                   AuditoriaService auditoriaService,
                                   RecuperacionClaveService recuperacionClaveService,
                                   PoliticaClaves politicaClaves,
                                   EmailService emailService) {
        this.personalRepository = personalRepository;
        this.perfilRepository = perfilRepository;
        this.turnoRepository = turnoRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.recuperacionClaveService = recuperacionClaveService;
        this.politicaClaves = politicaClaves;
        this.emailService = emailService;
    }

    @GetMapping
    public List<PersonalResponse> listar() {
        return personalRepository.findAll().stream()
                .map(PersonalResponse::de)
                .collect(Collectors.toList());
    }

    @GetMapping("/pendientes")
    public List<PersonalResponse> pendientes() {
        return personalRepository.findByEstadoAprobacionOrderByIdPersonalAsc("PENDIENTE")
                .stream()
                .map(PersonalResponse::de)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> crear(@Valid @RequestBody PersonalRequest request) {
        if (request.getHoraInicio() == null || request.getHoraFin() == null) {
            return ResponseEntity.badRequest()
                    .body("El turno (hora de inicio y hora de fin) es obligatorio al crear un usuario");
        }
        Perfil perfil = perfilRepository.findById(request.getIdPerfil()).orElse(null);
        if (perfil == null) return ResponseEntity.badRequest().body("Perfil no encontrado");

        String usuario = normalizarUsuario(request.getUsuario());
        String email = normalizarEmail(request.getEmail());

        if (personalRepository.existsByUsuarioIgnoreCase(usuario)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("El usuario ya existe");
        }
        if (personalRepository.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("El correo ya esta registrado");
        }
        if (personalRepository.existsByCedula(request.getCedula().trim())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("La cedula ya esta registrada");
        }

        Personal p = new Personal();
        p.setNombre(request.getNombre().trim());
        p.setApellido(request.getApellido().trim());
        p.setEmail(email);
        p.setCedula(request.getCedula().trim());
        p.setTelefono(recortar(request.getTelefono(), 20));
        p.setUsuario(usuario);
        // La cuenta nace sin clave utilizable: se activa por correo o con una
        // clave temporal generada despues por el administrador.
        p.setClave(passwordEncoder.encode(tokenAleatorio()));
        p.setPerfil(perfil);
        p.setIdEstado(0);
        p.setRequiere2fa(true);
        p.setDebeCambiarClave(false);
        p.setEstadoAprobacion("PENDIENTE");
        Personal guardado = personalRepository.save(p);

        Turno turno = new Turno();
        turno.setPersonal(guardado);
        turno.setHoraInicio(request.getHoraInicio());
        turno.setHoraFin(request.getHoraFin());
        turno.setIdEstado(1);
        turnoRepository.save(turno);

        auditoriaService.registrarAccionActual("CREAR",
                "Usuario: " + guardado.getUsuario() + " (" + perfil.getPerfil() + "), pendiente de aprobacion");
        return ResponseEntity.ok(PersonalResponse.de(guardado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @Valid @RequestBody PersonalRequest request) {
        return personalRepository.findById(id).map(p -> {
            Perfil perfil = perfilRepository.findById(request.getIdPerfil()).orElse(null);
            if (perfil == null) return ResponseEntity.badRequest().body("Perfil no encontrado");

            String email = normalizarEmail(request.getEmail());
            boolean cambiaEmail = !email.equalsIgnoreCase(p.getEmail());
            boolean activada = p.getFechaCambioClave() != null;

            if (cambiaEmail && activada) {
                return ResponseEntity.badRequest().body(
                        "La cuenta ya fue activada. El usuario debe cambiar su correo desde Mi cuenta.");
            }
            if (cambiaEmail) {
                boolean ocupado = personalRepository.findByEmailIgnoreCase(email)
                        .filter(otro -> !otro.getIdPersonal().equals(id))
                        .isPresent();
                if (ocupado) return ResponseEntity.status(HttpStatus.CONFLICT).body("El correo ya esta registrado");
                if (!activada) {
                    recuperacionClaveService.invalidarPendientes(p, RecuperacionClaveService.TIPO_ACTIVACION);
                }
            }

            boolean usuarioOcupado = personalRepository.findByUsuarioIgnoreCase(
                            normalizarUsuario(request.getUsuario()))
                    .filter(otro -> !otro.getIdPersonal().equals(id))
                    .isPresent();
            if (usuarioOcupado) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("El usuario ya existe");
            }

            p.setNombre(request.getNombre().trim());
            p.setApellido(request.getApellido().trim());
            p.setEmail(email);
            p.setCedula(request.getCedula().trim());
            p.setTelefono(recortar(request.getTelefono(), 20));
            p.setUsuario(normalizarUsuario(request.getUsuario()));
            p.setPerfil(perfil);

            // Una solicitud rechazada vuelve a la cola al editarla.
            if ("RECHAZADA".equals(p.getEstadoAprobacion())) {
                p.setEstadoAprobacion("PENDIENTE");
                p.setAprobadoPor(null);
                p.setFechaAprobacion(null);
                p.setIdEstado(0);
            }

            Personal guardado = personalRepository.save(p);

            if (request.getHoraInicio() != null && request.getHoraFin() != null) {
                List<Turno> turnosActivos = turnoRepository.findByPersonal_IdPersonalAndIdEstado(
                        guardado.getIdPersonal(), 1);
                if (!turnosActivos.isEmpty()) {
                    Turno turnoMasReciente = turnosActivos.stream()
                            .max((a, b) -> a.getIdTurno().compareTo(b.getIdTurno()))
                            .get();
                    turnoMasReciente.setHoraInicio(request.getHoraInicio());
                    turnoMasReciente.setHoraFin(request.getHoraFin());
                    turnoRepository.save(turnoMasReciente);
                    turnosActivos.stream()
                            .filter(t -> !t.getIdTurno().equals(turnoMasReciente.getIdTurno()))
                            .forEach(t -> { t.setIdEstado(0); turnoRepository.save(t); });
                } else {
                    Turno turno = new Turno();
                    turno.setPersonal(guardado);
                    turno.setHoraInicio(request.getHoraInicio());
                    turno.setHoraFin(request.getHoraFin());
                    turno.setIdEstado(1);
                    turnoRepository.save(turno);
                }
            }

            auditoriaService.registrarAccionActual("ACTUALIZAR", "Usuario: " + guardado.getUsuario());
            return ResponseEntity.ok(PersonalResponse.de(guardado));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstado(@PathVariable Integer id, @RequestParam Integer idEstado) {
        if (idEstado == null || (idEstado != 0 && idEstado != 1)) {
            return ResponseEntity.badRequest().body("El estado debe ser 0 o 1");
        }
        return personalRepository.findById(id).map(p -> {
            if (idEstado == 1 && !"APROBADA".equals(p.getEstadoAprobacion())) {
                return ResponseEntity.badRequest().body(
                        "La cuenta debe ser aprobada por un usuario de seguridad antes de activarse");
            }
            p.setIdEstado(idEstado);
            Personal guardado = personalRepository.save(p);
            auditoriaService.registrarAccionActual(idEstado == 1 ? "ACTIVAR" : "DESACTIVAR",
                    "Usuario: " + guardado.getUsuario());
            return ResponseEntity.ok(PersonalResponse.de(guardado));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<?> aprobar(@PathVariable Integer id, Principal principal) {
        Personal p = personalRepository.findById(id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        if (!"PENDIENTE".equals(p.getEstadoAprobacion())) {
            return ResponseEntity.badRequest().body("La cuenta no esta pendiente de aprobacion");
        }

        Integer idAprobador = personalRepository.findByUsuarioIgnoreCase(principal.getName())
                .map(Personal::getIdPersonal).orElse(null);

        p.setEstadoAprobacion("APROBADA");
        p.setIdEstado(1);
        p.setAprobadoPor(idAprobador);
        p.setFechaAprobacion(LocalDateTime.now());
        personalRepository.save(p);

        boolean enviado = enviarActivacion(p);
        auditoriaService.registrarAccionActual("APROBAR", "Usuario: " + p.getUsuario());

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("message", enviado
                ? "Cuenta aprobada. Enviamos el enlace de activacion al correo del usuario."
                : "Cuenta aprobada, pero no se pudo enviar el correo. Reintenta la activacion.");
        respuesta.put("correoEnviado", enviado);
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<?> rechazar(@PathVariable Integer id,
                                      @RequestBody(required = false) RechazarRequest request) {
        Personal p = personalRepository.findById(id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        if (!"PENDIENTE".equals(p.getEstadoAprobacion())) {
            return ResponseEntity.badRequest().body("La cuenta no esta pendiente de aprobacion");
        }
        p.setEstadoAprobacion("RECHAZADA");
        p.setIdEstado(0);
        personalRepository.save(p);

        String motivo = request == null ? null : recortar(request.getMotivo(), 100);
        auditoriaService.registrarAccionActual("RECHAZAR",
                "Usuario: " + p.getUsuario() + (motivo == null || motivo.isBlank() ? "" : ". Motivo: " + motivo));
        return ResponseEntity.ok(Map.of("message", "Solicitud rechazada."));
    }

    @PostMapping("/{id}/activacion")
    public ResponseEntity<?> reenviarActivacion(@PathVariable Integer id) {
        Personal p = personalRepository.findById(id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        if (!"APROBADA".equals(p.getEstadoAprobacion())) {
            return ResponseEntity.badRequest().body("La cuenta aun no esta aprobada");
        }
        if (p.getFechaCambioClave() != null) {
            return ResponseEntity.badRequest().body("La cuenta ya fue activada");
        }
        boolean enviado = enviarActivacion(p);
        auditoriaService.registrarAccionActual("ACTIVACION", "Reenvio a usuario: " + p.getUsuario());
        return ResponseEntity.ok(Map.of("correoEnviado", enviado,
                "message", enviado ? "Enlace de activacion reenviado." : "No se pudo enviar el correo."));
    }

    @PostMapping("/{id}/restablecer")
    public ResponseEntity<?> restablecer(@PathVariable Integer id) {
        Personal p = personalRepository.findById(id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        if (!"APROBADA".equals(p.getEstadoAprobacion()) || p.getFechaCambioClave() == null) {
            return ResponseEntity.badRequest().body(
                    "La cuenta no esta activada. Usa el reenvio de activacion.");
        }
        boolean enviado = enviarRecuperacion(p);
        auditoriaService.registrarAccionActual("RESET", "Enlace enviado a usuario: " + p.getUsuario());
        return ResponseEntity.ok(Map.of("correoEnviado", enviado,
                "message", enviado ? "Enlace de restablecimiento enviado." : "No se pudo enviar el correo."));
    }

    @PostMapping("/{id}/clave-temporal")
    public ResponseEntity<?> claveTemporal(@PathVariable Integer id) {
        Personal p = personalRepository.findById(id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        if (!"APROBADA".equals(p.getEstadoAprobacion()) || p.getFechaCambioClave() != null) {
            return ResponseEntity.badRequest().body(
                    "Solo se puede generar una clave temporal antes de la primera activacion.");
        }

        String clave = generarClaveTemporal(p);
        LocalDateTime expira = LocalDateTime.now().plusHours(72);
        p.setClave(passwordEncoder.encode(clave));
        p.setDebeCambiarClave(true);
        p.setClaveExpira(expira);
        personalRepository.save(p);

        auditoriaService.registrarAccionActual("CLAVE_TEMPORAL", "Usuario: " + p.getUsuario());
        return ResponseEntity.ok(new ClaveTemporalResponse(clave, expira));
    }

    private boolean enviarActivacion(Personal personal) {
        RecuperacionClaveService.TokenGenerado token = recuperacionClaveService.generar(
                personal, RecuperacionClaveService.TIPO_ACTIVACION, null, null);
        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombre", personal.getNombre() + " " + personal.getApellido());
        modelo.put("usuario", personal.getUsuario());
        modelo.put("perfil", nombrePerfil(personal));
        modelo.put("enlace", recuperacionClaveService.enlace(token.token()));
        modelo.put("destinatario", personal.getEmail());
        modelo.put("horas", 72);
        try {
            emailService.enviarHtml(personal.getEmail(),
                    "[" + personal.getUsuario() + "] Activa tu cuenta - SOS Clinic",
                    emailService.renderizar("plantillas/activacion-cuenta", modelo));
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar la activacion al usuario {}", personal.getUsuario());
            return false;
        }
    }

    private boolean enviarRecuperacion(Personal personal) {
        RecuperacionClaveService.TokenGenerado token = recuperacionClaveService.generar(
                personal, RecuperacionClaveService.TIPO_RECUPERACION, null, null);
        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombre", personal.getNombre() + " " + personal.getApellido());
        modelo.put("usuario", personal.getUsuario());
        modelo.put("perfil", nombrePerfil(personal));
        modelo.put("enlace", recuperacionClaveService.enlace(token.token()));
        modelo.put("destinatario", personal.getEmail());
        modelo.put("minutos", 30);
        try {
            emailService.enviarHtml(personal.getEmail(),
                    "[" + personal.getUsuario() + "] Restablece tu clave - SOS Clinic",
                    emailService.renderizar("plantillas/recuperacion-clave", modelo));
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar la recuperacion al usuario {}", personal.getUsuario());
            return false;
        }
    }

    private String generarClaveTemporal(Personal personal) {
        PoliticaClaves.Contexto contexto = PoliticaClaves.Contexto.de(personal);
        for (int intento = 0; intento < 100; intento++) {
            StringBuilder sb = new StringBuilder(14);
            for (int i = 0; i < 14; i++) {
                sb.append(CARACTERES_TEMPORAL.charAt(random.nextInt(CARACTERES_TEMPORAL.length())));
            }
            String candidata = sb.toString();
            if (politicaClaves.validar(candidata, contexto).isEmpty()) {
                return candidata;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo generar una clave temporal valida");
    }

    private String tokenAleatorio() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizarUsuario(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private static String nombrePerfil(Personal personal) {
        if (personal == null || personal.getPerfil() == null || personal.getPerfil().getPerfil() == null) {
            return "";
        }
        return personal.getPerfil().getPerfil().trim();
    }

    private static String normalizarEmail(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null) return null;
        String limpio = valor.trim();
        if (limpio.isEmpty()) return null;
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }
}
