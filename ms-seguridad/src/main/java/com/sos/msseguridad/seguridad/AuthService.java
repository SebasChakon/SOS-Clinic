package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.dto.ActividadDTO;
import com.sos.msseguridad.dto.CambioEmailRequest;
import com.sos.msseguridad.dto.CambiarClaveRequest;
import com.sos.msseguridad.dto.ConfirmarEmailRequest;
import com.sos.msseguridad.dto.Desactivar2faRequest;
import com.sos.msseguridad.dto.LoginRequest;
import com.sos.msseguridad.dto.LoginResponse;
import com.sos.msseguridad.dto.PersonalResponse;
import com.sos.msseguridad.dto.RecuperarClaveRequest;
import com.sos.msseguridad.dto.Reenviar2faRequest;
import com.sos.msseguridad.dto.RestablecerClaveRequest;
import com.sos.msseguridad.dto.TeleconsultaResponse;
import com.sos.msseguridad.dto.ValidarTokenResponse;
import com.sos.msseguridad.dto.VerificarCodigoRequest;
import com.sos.msseguridad.modelo.CodigoVerificacion;
import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.modelo.TokenRecuperacion;
import com.sos.msseguridad.repositorio.CodigoVerificacionRepository;
import com.sos.msseguridad.repositorio.PerfilActividadRepository;
import com.sos.msseguridad.repositorio.PersonalRepository;
import com.sos.msseguridad.repositorio.TurnoRepository;
import com.sos.msseguridad.servicio.AuditoriaService;
import com.sos.msseguridad.servicio.EmailService;
import com.sos.msseguridad.servicio.TeleconsultaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String MENSAJE_CREDENCIALES = "Usuario o clave incorrectos";
    private static final String MENSAJE_CODIGO = "Codigo incorrecto o expirado";
    private static final String PROPOSITO_LOGIN = "LOGIN";
    private static final String PROPOSITO_DESACTIVAR_2FA = "DESACTIVAR_2FA";
    private static final String MENSAJE_RECUPERACION =
            "Si el usuario o correo esta registrado, enviamos un enlace para restablecer la clave.";

    private final PersonalRepository personalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SesionService sesionService;
    private final TurnoRepository turnoRepository;
    private final PerfilActividadRepository perfilActividadRepository;
    private final CodigoVerificacionRepository codigoVerificacionRepository;
    private final RecuperacionClaveService recuperacionClaveService;
    private final PoliticaClaves politicaClaves;
    private final EmailService emailService;
    private final TeleconsultaService teleconsultaService;
    private final LoginAttemptService loginAttemptService;
    private final AuditoriaService auditoriaService;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyHash;

    public AuthService(PersonalRepository personalRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, SesionService sesionService, TurnoRepository turnoRepository,
                       PerfilActividadRepository perfilActividadRepository,
                       CodigoVerificacionRepository codigoVerificacionRepository,
                       RecuperacionClaveService recuperacionClaveService,
                       PoliticaClaves politicaClaves,
                       EmailService emailService, TeleconsultaService teleconsultaService,
                       LoginAttemptService loginAttemptService, AuditoriaService auditoriaService) {
        this.personalRepository = personalRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sesionService = sesionService;
        this.turnoRepository = turnoRepository;
        this.perfilActividadRepository = perfilActividadRepository;
        this.codigoVerificacionRepository = codigoVerificacionRepository;
        this.recuperacionClaveService = recuperacionClaveService;
        this.politicaClaves = politicaClaves;
        this.emailService = emailService;
        this.teleconsultaService = teleconsultaService;
        this.loginAttemptService = loginAttemptService;
        this.auditoriaService = auditoriaService;
        this.dummyHash = passwordEncoder.encode("hash-dummy-para-igualar-tiempos");
    }

    /* ------------------------------------------------------------------ */
    /* Inicio de sesion y segundo factor                                   */
    /* ------------------------------------------------------------------ */

    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String usuario = normalizar(request.getUsuario());
        String claveIntentos = normalizarUsuario(usuario);

        if (loginAttemptService.estaBloqueado(claveIntentos)) {
            throw new RateLimitExceededException(
                    "Demasiados intentos fallidos. Intenta de nuevo mas tarde.",
                    loginAttemptService.segundosRestantes(claveIntentos));
        }

        Personal personal;
        try {
            personal = validarCredencialesYTurno(usuario, request.getClave());
        } catch (BadCredentialsException e) {
            // No se crean contadores para usuarios inexistentes: evita que un
            // atacante llene el mapa en memoria con nombres inventados.
            if (personalRepository.findByUsuarioIgnoreCase(usuario).isPresent()) {
                loginAttemptService.registrarFallo(claveIntentos);
            }
            auditoriaService.registrar(usuario, "LOGIN_FALLIDO", "Intento de inicio de sesion fallido");
            throw e;
        }

        loginAttemptService.registrarExito(claveIntentos);

        if (Boolean.TRUE.equals(personal.getRequiere2fa())) {
            CodigoEnviado enviado = enviarCodigo(personal, PROPOSITO_LOGIN, claveIntentos);
            return new LoginResponse(null, personal.getUsuario(), null, null, null, true,
                    enviado.challenge(), Boolean.TRUE.equals(personal.getDebeCambiarClave()));
        }

        return construirRespuestaConToken(personal, ip, userAgent);
    }

    public LoginResponse verificarCodigo(VerificarCodigoRequest request, String ip, String userAgent) {
        String challenge = request.getChallenge() == null ? "" : request.getChallenge().trim();
        String challengeHash = RecuperacionClaveService.hash(challenge);
        String claveIntentos = "2fa:" + challengeHash;

        if (loginAttemptService.estaBloqueado(claveIntentos)) {
            throw new RateLimitExceededException(
                    "Demasiados intentos fallidos. Intenta de nuevo mas tarde.",
                    loginAttemptService.segundosRestantes(claveIntentos));
        }

        CodigoVerificacion codigo = codigoVerificacionRepository
                .findTopByChallengeHashAndPropositoAndUsadoFalseOrderByIdCodigoDesc(challengeHash, PROPOSITO_LOGIN)
                .orElse(null);

        if (codigo == null) {
            passwordEncoder.matches("000000", dummyHash);
            throw new BadCredentialsException(MENSAJE_CODIGO);
        }
        if (codigo.getFechaExpira().isBefore(LocalDateTime.now())) {
            passwordEncoder.matches("000000", dummyHash);
            loginAttemptService.registrarFallo(claveIntentos);
            throw new BadCredentialsException(MENSAJE_CODIGO);
        }

        Personal personal = codigo.getPersonal();
        if (!cuentaOperativa(personal)) {
            passwordEncoder.matches("000000", dummyHash);
            loginAttemptService.registrarFallo(claveIntentos);
            throw new BadCredentialsException(MENSAJE_CODIGO);
        }

        if (request.getCodigo() == null
                || !passwordEncoder.matches(request.getCodigo().trim(), codigo.getCodigo())) {
            registrarFallo2fa(claveIntentos, codigo);
            throw new BadCredentialsException(MENSAJE_CODIGO);
        }

        codigo.setUsado(true);
        codigoVerificacionRepository.save(codigo);
        loginAttemptService.registrarExito(claveIntentos);

        return construirRespuestaConToken(personal, ip, userAgent);
    }

    public LoginResponse refrescar(String refreshToken) {
        SesionService.SesionRenovada renovada = sesionService.renovar(refreshToken);
        Personal personal = renovada.personal();

        if (!cuentaOperativa(personal)) {
            sesionService.revocarTodas(personal, "CUENTA_INACTIVA");
            throw new BadCredentialsException("Sesion expirada. Vuelve a iniciar sesion.");
        }

        return construirRespuestaConToken(personal, renovada.token(), renovada.fechaExpira());
    }

    public void logout(String refreshToken) {
        sesionService.revocar(refreshToken, "LOGOUT");
    }

    public LoginResponse reenviarCodigo(Reenviar2faRequest request) {
        String challengeHash = RecuperacionClaveService.hash(
                request.getChallenge() == null ? "" : request.getChallenge().trim());
        CodigoVerificacion anterior = codigoVerificacionRepository
                .findTopByChallengeHashAndPropositoAndUsadoFalseOrderByIdCodigoDesc(challengeHash, PROPOSITO_LOGIN)
                .orElse(null);

        if (anterior == null || anterior.getFechaExpira().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("El codigo expiro. Vuelve a iniciar sesion.");
        }

        Personal personal = anterior.getPersonal();
        if (!cuentaOperativa(personal)) {
            throw new BadCredentialsException("El codigo expiro. Vuelve a iniciar sesion.");
        }
        CodigoEnviado nuevo = enviarCodigo(personal, PROPOSITO_LOGIN,
                normalizarUsuario(personal.getUsuario()));
        return new LoginResponse(null, personal.getUsuario(), null, null, null, true,
                nuevo.challenge(), Boolean.TRUE.equals(personal.getDebeCambiarClave()));
    }

    private record CodigoEnviado(String challenge) {}

    private CodigoEnviado enviarCodigo(Personal personal, String proposito, String claveLimite) {
        if (!loginAttemptService.puedeReenviar(claveLimite)) {
            throw new RateLimitExceededException(
                    "Demasiados codigos solicitados. Intenta de nuevo mas tarde.",
                    loginAttemptService.segundosParaReenvio(claveLimite));
        }
        if (personal.getEmail() == null || personal.getEmail().isBlank()) {
            throw new BadCredentialsException(
                    "No se pudo enviar el codigo de verificacion. Contacta al administrador.");
        }

        codigoVerificacionRepository.invalidarPendientes(personal.getIdPersonal());

        String codigoGenerado = String.format("%06d", secureRandom.nextInt(1_000_000));
        String challenge = tokenAleatorio();

        CodigoVerificacion cv = new CodigoVerificacion();
        cv.setPersonal(personal);
        cv.setCodigo(passwordEncoder.encode(codigoGenerado));
        cv.setChallengeHash(RecuperacionClaveService.hash(challenge));
        cv.setProposito(proposito);
        cv.setFechaExpira(LocalDateTime.now().plusMinutes(5));
        cv.setUsado(false);
        codigoVerificacionRepository.save(cv);

        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombre", personal.getNombre() + " " + personal.getApellido());
        modelo.put("usuario", personal.getUsuario());
        modelo.put("perfil", nombrePerfil(personal));
        modelo.put("codigo", codigoGenerado);
        modelo.put("destinatario", personal.getEmail());
        modelo.put("motivo", PROPOSITO_DESACTIVAR_2FA.equals(proposito)
                ? "desactivar la verificacion en dos pasos"
                : "completar tu inicio de sesion");

        try {
            emailService.enviarHtml(personal.getEmail(),
                    "[" + personal.getUsuario() + "] Codigo de verificacion - SOS Clinic",
                    emailService.renderizar("plantillas/codigo-verificacion", modelo));
        } catch (Exception e) {
            cv.setUsado(true);
            codigoVerificacionRepository.save(cv);
            log.warn("No se pudo enviar el codigo 2FA al usuario {}", personal.getUsuario());
            throw new BadCredentialsException(
                    "No se pudo enviar el codigo de verificacion. Intenta de nuevo mas tarde.");
        }

        loginAttemptService.registrarReenvio(claveLimite);
        log.info("Codigo 2FA generado para el usuario {}", personal.getUsuario());
        return new CodigoEnviado(challenge);
    }

    private void registrarFallo2fa(String claveIntentos, CodigoVerificacion codigo) {
        loginAttemptService.registrarFallo(claveIntentos);
        if (loginAttemptService.estaBloqueado(claveIntentos) && codigo != null) {
            codigo.setUsado(true);
            codigoVerificacionRepository.save(codigo);
        }
    }

    private Personal validarCredencialesYTurno(String usuario, String clave) {
        Personal personal = personalRepository.findByUsuarioIgnoreCase(usuario).orElse(null);

        if (personal == null) {
            passwordEncoder.matches(clave == null ? "" : clave, dummyHash);
            throw new BadCredentialsException(MENSAJE_CREDENCIALES);
        }

        if (!cuentaOperativa(personal)) {
            passwordEncoder.matches(clave == null ? "" : clave, dummyHash);
            throw new BadCredentialsException(MENSAJE_CREDENCIALES);
        }

        if (clave == null || !passwordEncoder.matches(clave, personal.getClave())) {
            throw new BadCredentialsException(MENSAJE_CREDENCIALES);
        }

        if (personal.getClaveExpira() != null && personal.getClaveExpira().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Tu clave temporal vencio. Pide un nuevo restablecimiento al administrador.");
        }

        if (!puedeIniciarSesion(personal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes iniciar sesion en este momento.");
        }

        return personal;
    }

    private boolean cuentaOperativa(Personal personal) {
        return personal != null
                && Integer.valueOf(1).equals(personal.getIdEstado())
                && "APROBADA".equals(personal.getEstadoAprobacion());
    }

    private boolean puedeIniciarSesion(Personal personal) {
        String perfil = perfil(personal);
        // Administracion y seguridad deben poder aprobar o atender incidencias
        // fuera del turno operativo; el resto solo entra dentro de su turno.
        boolean sinTurno = "administrador".equals(perfil) || "seguridad".equals(perfil);
        return sinTurno || dentroDeTurno(personal.getIdPersonal());
    }

    /* ------------------------------------------------------------------ */
    /* Cambio y recuperacion de clave                                      */
    /* ------------------------------------------------------------------ */

    public LoginResponse cambiarClave(String usuario, CambiarClaveRequest request,
                                      String ip, String userAgent) {
        Personal personal = buscarOperativo(usuario);
        String claveIntentos = "cambio:" + normalizarUsuario(usuario);

        if (loginAttemptService.estaBloqueado(claveIntentos)) {
            throw new RateLimitExceededException(
                    "Demasiados intentos fallidos. Intenta de nuevo mas tarde.",
                    loginAttemptService.segundosRestantes(claveIntentos));
        }

        if (request.getClaveActual() == null
                || !passwordEncoder.matches(request.getClaveActual(), personal.getClave())) {
            loginAttemptService.registrarFallo(claveIntentos);
            throw new BadCredentialsException("La clave actual no es correcta.");
        }
        loginAttemptService.registrarExito(claveIntentos);

        validarPolitica(request.getClaveNueva(), PoliticaClaves.Contexto.de(personal, request.getClaveActual()));

        personal.setClave(passwordEncoder.encode(request.getClaveNueva()));
        personal.setDebeCambiarClave(false);
        personal.setClaveExpira(null);
        personal.setFechaCambioClave(LocalDateTime.now());
        personalRepository.save(personal);

        invalidarCredencialesPendientes(personal);
        sesionService.revocarTodas(personal, "CAMBIO_CLAVE");
        auditoriaService.registrar(personal.getUsuario(), "CAMBIO_CLAVE", "Cambio de clave propio");
        enviarAviso(personal, "clave-actualizada", "Tu clave fue actualizada - SOS Clinic",
                "Tu clave de acceso a SOS Clinic fue actualizada correctamente.");

        return construirRespuestaConToken(personal, ip, userAgent);
    }

    public void recuperarClave(RecuperarClaveRequest request, String ip, String userAgent) {
        String identificador = normalizar(request.getIdentificador());
        if (identificador.isBlank()) return;

        Personal personal = personalRepository.findByUsuarioIgnoreCase(identificador)
                .or(() -> personalRepository.findByEmailIgnoreCase(identificador))
                .orElse(null);

        if (personal == null || !cuentaOperativa(personal)) {
            simularTrabajoDeClave();
            return;
        }

        String claveLimite = "recuperacion:" + personal.getIdPersonal();
        if (!loginAttemptService.puedeReenviar(claveLimite)) {
            log.info("Solicitud de recuperacion limitada para el usuario {}", personal.getUsuario());
            simularTrabajoDeClave();
            return;
        }

        RecuperacionClaveService.TokenGenerado token = recuperacionClaveService.generar(
                personal, RecuperacionClaveService.TIPO_RECUPERACION, ip, userAgent);

        // El envio SMTP es la parte mas lenta y no debe delatar, por tiempo de
        // respuesta, si el identificador correspondia a una cuenta real.
        loginAttemptService.registrarReenvio(claveLimite);
        auditoriaService.registrar(personal.getUsuario(), "RESET", "Solicitud de recuperacion");

        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombre", personal.getNombre() + " " + personal.getApellido());
        modelo.put("usuario", personal.getUsuario());
        modelo.put("perfil", nombrePerfil(personal));
        modelo.put("enlace", recuperacionClaveService.enlace(token.token()));
        modelo.put("destinatario", personal.getEmail());
        modelo.put("minutos", 30);

        CompletableFuture.runAsync(() -> {
            try {
                emailService.enviarHtml(personal.getEmail(),
                        "[" + personal.getUsuario() + "] Restablece tu clave - SOS Clinic",
                        emailService.renderizar("plantillas/recuperacion-clave", modelo));
            } catch (Exception e) {
                log.warn("No se pudo enviar el correo de recuperacion al usuario {}", personal.getUsuario());
            }
        });

        simularTrabajoDeClave();
    }

    private void simularTrabajoDeClave() {
        passwordEncoder.matches("000000", dummyHash);
    }

    public ValidarTokenResponse validarTokenRecuperacion(String token) {
        TokenRecuperacion registro = recuperacionClaveService.validar(token, null);
        return new ValidarTokenResponse(true, registro.getTipo(), registro.getPersonal().getUsuario());
    }

    public void restablecerClave(RestablecerClaveRequest request) {
        TokenRecuperacion registro = recuperacionClaveService.validar(request.getToken(), null);
        if (RecuperacionClaveService.TIPO_CAMBIO_EMAIL.equals(registro.getTipo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El enlace no es valido, ya fue usado o expiro.");
        }

        Personal personal = registro.getPersonal();
        if (!cuentaOperativa(personal)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta no esta habilitada para restablecer la clave.");
        }

        validarPolitica(request.getClave(), PoliticaClaves.Contexto.de(personal));

        personal.setClave(passwordEncoder.encode(request.getClave()));
        personal.setDebeCambiarClave(false);
        personal.setClaveExpira(null);
        personal.setFechaCambioClave(LocalDateTime.now());
        personalRepository.save(personal);

        recuperacionClaveService.marcarUsado(registro);
        invalidarCredencialesPendientes(personal);
        sesionService.revocarTodas(personal, "RESET_CLAVE");

        String accion = RecuperacionClaveService.TIPO_ACTIVACION.equals(registro.getTipo())
                ? "ACTIVACION" : "RESET";
        auditoriaService.registrar(personal.getUsuario(), accion, "Clave establecida mediante enlace");
        enviarAviso(personal, "clave-actualizada", "Tu clave fue actualizada - SOS Clinic",
                "Tu clave de acceso a SOS Clinic fue establecida correctamente.");
    }

    /* ------------------------------------------------------------------ */
    /* Correo                                                              */
    /* ------------------------------------------------------------------ */

    public void solicitarCambioEmail(String usuario, CambioEmailRequest request,
                                     String ip, String userAgent) {
        Personal personal = buscarOperativo(usuario);

        if (request.getClaveActual() == null
                || !passwordEncoder.matches(request.getClaveActual(), personal.getClave())) {
            throw new BadCredentialsException("La clave actual no es correcta.");
        }

        String nuevo = normalizar(request.getEmailNuevo()).toLowerCase(Locale.ROOT);
        if (nuevo.equalsIgnoreCase(personal.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo nuevo es igual al actual.");
        }
        if (personalRepository.existsByEmailIgnoreCase(nuevo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese correo ya esta registrado.");
        }

        String claveLimite = "email:" + personal.getIdPersonal();
        if (!loginAttemptService.puedeReenviar(claveLimite)) {
            throw new RateLimitExceededException(
                    "Demasiadas solicitudes de cambio de correo. Intenta mas tarde.",
                    loginAttemptService.segundosParaReenvio(claveLimite));
        }

        personal.setEmailPendiente(nuevo);
        personalRepository.save(personal);

        RecuperacionClaveService.TokenGenerado token = recuperacionClaveService.generar(
                personal, RecuperacionClaveService.TIPO_CAMBIO_EMAIL, ip, userAgent);

        Map<String, Object> modeloNuevo = new LinkedHashMap<>();
        modeloNuevo.put("nombre", personal.getNombre() + " " + personal.getApellido());
        modeloNuevo.put("usuario", personal.getUsuario());
        modeloNuevo.put("perfil", nombrePerfil(personal));
        modeloNuevo.put("enlace", recuperacionClaveService.enlace(token.token()));
        modeloNuevo.put("destinatario", nuevo);
        modeloNuevo.put("emailNuevo", nuevo);

        Map<String, Object> modeloAnterior = new LinkedHashMap<>();
        modeloAnterior.put("nombre", personal.getNombre() + " " + personal.getApellido());
        modeloAnterior.put("usuario", personal.getUsuario());
        modeloAnterior.put("perfil", nombrePerfil(personal));
        modeloAnterior.put("destinatario", personal.getEmail());
        modeloAnterior.put("emailNuevo", nuevo);

        try {
            emailService.enviarHtml(nuevo,
                    "[" + personal.getUsuario() + "] Confirma tu nuevo correo - SOS Clinic",
                    emailService.renderizar("plantillas/correo-nuevo", modeloNuevo));
        } catch (Exception e) {
            log.warn("No se pudo enviar la verificacion de correo al usuario {}", personal.getUsuario());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo enviar el correo de verificacion. Intenta de nuevo.");
        }

        // El aviso al correo anterior es secundario: no debe bloquear la respuesta.
        CompletableFuture.runAsync(() -> enviarCorreo(personal.getEmail(),
                "[" + personal.getUsuario() + "] Solicitud de cambio de correo - SOS Clinic",
                "plantillas/aviso-correo-anterior", modeloAnterior));

        loginAttemptService.registrarReenvio(claveLimite);
        auditoriaService.registrar(personal.getUsuario(), "CAMBIO_EMAIL",
                "Solicitud de cambio de correo a " + nuevo);
    }

    public void confirmarCambioEmail(ConfirmarEmailRequest request) {
        TokenRecuperacion registro = recuperacionClaveService.validar(
                request.getToken(), RecuperacionClaveService.TIPO_CAMBIO_EMAIL);
        Personal personal = registro.getPersonal();
        if (!cuentaOperativa(personal)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta no esta habilitada para cambiar el correo.");
        }
        String nuevo = personal.getEmailPendiente();

        if (nuevo == null || nuevo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay un cambio de correo pendiente.");
        }
        if (personalRepository.existsByEmailIgnoreCase(nuevo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese correo ya esta registrado.");
        }

        String anterior = personal.getEmail();
        personal.setEmail(nuevo);
        personal.setEmailPendiente(null);
        personalRepository.save(personal);

        recuperacionClaveService.marcarUsado(registro);
        recuperacionClaveService.invalidarPendientes(personal, RecuperacionClaveService.TIPO_CAMBIO_EMAIL);

        auditoriaService.registrar(personal.getUsuario(), "CAMBIO_EMAIL",
                "Correo actualizado de " + anterior + " a " + nuevo);

        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombre", personal.getNombre() + " " + personal.getApellido());
        modelo.put("usuario", personal.getUsuario());
        modelo.put("perfil", nombrePerfil(personal));
        modelo.put("email", nuevo);
        modelo.put("destinatario", nuevo);

        Map<String, Object> modeloAnterior = new LinkedHashMap<>(modelo);
        modeloAnterior.put("destinatario", anterior);

        CompletableFuture.runAsync(() -> {
            enviarCorreo(nuevo,
                    "[" + personal.getUsuario() + "] Correo actualizado - SOS Clinic",
                    "plantillas/correo-actualizado", modelo);
            enviarCorreo(anterior,
                    "[" + personal.getUsuario() + "] Tu correo fue actualizado - SOS Clinic",
                    "plantillas/correo-actualizado", modeloAnterior);
        });
    }

    /* ------------------------------------------------------------------ */
    /* 2FA                                                                 */
    /* ------------------------------------------------------------------ */

    public void activar2fa(String usuario) {
        Personal personal = buscarOperativo(usuario);
        personal.setRequiere2fa(true);
        personalRepository.save(personal);
        auditoriaService.registrar(personal.getUsuario(), "2FA_ON", "Verificacion en dos pasos activada");
        enviarAviso(personal, "aviso-seguridad", "Verificacion en dos pasos activada - SOS Clinic",
                "La verificacion en dos pasos quedo activada en tu cuenta.");
    }

    public String solicitarDesactivar2fa(String usuario, String claveActual) {
        Personal personal = buscarOperativo(usuario);
        if (esPrivilegiado(personal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La verificacion en dos pasos es obligatoria para tu perfil.");
        }
        if (claveActual == null || !passwordEncoder.matches(claveActual, personal.getClave())) {
            throw new BadCredentialsException("La clave actual no es correcta.");
        }
        CodigoEnviado enviado = enviarCodigo(personal, PROPOSITO_DESACTIVAR_2FA,
                "2fa-off:" + normalizarUsuario(usuario));
        return enviado.challenge();
    }

    public void desactivar2fa(String usuario, Desactivar2faRequest request) {
        Personal personal = buscarOperativo(usuario);
        if (esPrivilegiado(personal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La verificacion en dos pasos es obligatoria para tu perfil.");
        }

        String claveIntentos = "2fa-off:" + normalizarUsuario(usuario);
        if (loginAttemptService.estaBloqueado(claveIntentos)) {
            throw new RateLimitExceededException(
                    "Demasiados intentos fallidos. Intenta de nuevo mas tarde.",
                    loginAttemptService.segundosRestantes(claveIntentos));
        }

        String hash = RecuperacionClaveService.hash(
                request.getChallenge() == null ? "" : request.getChallenge().trim());
        CodigoVerificacion codigo = codigoVerificacionRepository
                .findTopByChallengeHashAndPropositoAndUsadoFalseOrderByIdCodigoDesc(hash, PROPOSITO_DESACTIVAR_2FA)
                .orElse(null);

        if (codigo == null || codigo.getFechaExpira().isBefore(LocalDateTime.now())
                || !codigo.getPersonal().getIdPersonal().equals(personal.getIdPersonal())
                || request.getCodigo() == null
                || !passwordEncoder.matches(request.getCodigo().trim(), codigo.getCodigo())) {
            registrarFallo2fa(claveIntentos, codigo);
            throw new BadCredentialsException(MENSAJE_CODIGO);
        }

        codigo.setUsado(true);
        codigoVerificacionRepository.save(codigo);
        loginAttemptService.registrarExito(claveIntentos);

        personal.setRequiere2fa(false);
        personalRepository.save(personal);
        auditoriaService.registrar(personal.getUsuario(), "2FA_OFF", "Verificacion en dos pasos desactivada");
        enviarAviso(personal, "aviso-seguridad", "Verificacion en dos pasos desactivada - SOS Clinic",
                "La verificacion en dos pasos quedo desactivada en tu cuenta. Si no fuiste tu, cambia tu clave.");
    }

    /* ------------------------------------------------------------------ */
    /* Cuenta y utilidades                                                 */
    /* ------------------------------------------------------------------ */

    public PersonalResponse miCuenta(String usuario) {
        return PersonalResponse.de(buscarOperativo(usuario));
    }

    public TeleconsultaResponse enviarConfirmacionTeleconsulta(String usuario, String nombrePaciente) {
        Personal personal = personalRepository.findByUsuarioIgnoreCase(usuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario autenticado no encontrado"));

        if (personal.getEmail() == null || personal.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El usuario que dio de alta no tiene correo registrado");
        }
        if (personal.getCedula() == null || personal.getCedula().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El usuario que dio de alta no tiene documento registrado");
        }

        TeleconsultaService.CitaTeleconsulta cita = teleconsultaService.generarCita(personal.getCedula());
        teleconsultaService.confirmarPorCorreo(personal, nombrePaciente, cita);

        return new TeleconsultaResponse(cita.fechaHora(), cita.enlace(),
                cita.inicio().toString(), cita.fin().toString());
    }

    private Personal buscarOperativo(String usuario) {
        Personal personal = personalRepository.findByUsuarioIgnoreCase(normalizar(usuario))
                .orElseThrow(() -> new BadCredentialsException("Usuario autenticado no encontrado"));
        if (!cuentaOperativa(personal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta no esta habilitada.");
        }
        return personal;
    }

    private void validarPolitica(String clave, PoliticaClaves.Contexto contexto) {
        List<String> errores = politicaClaves.validar(clave, contexto);
        if (!errores.isEmpty()) {
            throw new PoliticaClaveException(errores);
        }
    }

    private void invalidarCredencialesPendientes(Personal personal) {
        codigoVerificacionRepository.invalidarPendientes(personal.getIdPersonal());
        recuperacionClaveService.invalidarTodos(personal);
    }

    private void enviarAviso(Personal personal, String plantilla, String asunto, String detalle) {
        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nombre", personal.getNombre() + " " + personal.getApellido());
        modelo.put("usuario", personal.getUsuario());
        modelo.put("perfil", nombrePerfil(personal));
        modelo.put("detalle", detalle);
        modelo.put("destinatario", personal.getEmail());
        CompletableFuture.runAsync(() ->
                enviarCorreo(personal.getEmail(),
                        "[" + personal.getUsuario() + "] " + asunto,
                        "plantillas/" + plantilla, modelo));
    }

    private void enviarCorreo(String destinatario, String asunto, String plantilla, Map<String, Object> modelo) {
        try {
            emailService.enviarHtml(destinatario, asunto, emailService.renderizar(plantilla, modelo));
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo [{}] a {}", asunto, destinatario);
        }
    }

    private LoginResponse construirRespuestaConToken(Personal personal, String ip, String userAgent) {
        SesionService.SesionCreada sesion = sesionService.crear(personal, ip, userAgent);
        return construirRespuestaConToken(personal, sesion.token(), sesion.fechaExpira());
    }

    private LoginResponse construirRespuestaConToken(Personal personal, String refreshToken,
                                                      LocalDateTime sesionExpira) {
        List<ActividadDTO> actividades = perfilActividadRepository
                .findByPerfil_IdPerfilAndIdEstado(personal.getPerfil().getIdPerfil(), 1)
                .stream()
                .map(pa -> new ActividadDTO(pa.getActividad().getNombreActividad(), pa.getActividad().getEnlace()))
                .collect(Collectors.toList());

        List<String> enlaces = actividades.stream()
                .map(ActividadDTO::getEnlace)
                .toList();

        boolean debeCambiarClave = Boolean.TRUE.equals(personal.getDebeCambiarClave());
        String token = jwtService.generarToken(personal.getUsuario(), personal.getIdPersonal(),
                personal.getPerfil().getPerfil(), enlaces, debeCambiarClave);
        long sesionExpiraEn = Math.max(0,
                Duration.between(LocalDateTime.now(), sesionExpira).toSeconds());

        return new LoginResponse(token, personal.getUsuario(), personal.getPerfil().getPerfil(),
                personal.getIdPersonal(), actividades, false, null, debeCambiarClave,
                refreshToken, jwtService.getExpiracionSegundos(), sesionExpiraEn);
    }

    private boolean dentroDeTurno(Integer idPersonal) {
        List<com.sos.msseguridad.modelo.Turno> turnos =
                turnoRepository.findByPersonal_IdPersonalAndIdEstado(idPersonal, 1);
        LocalTime ahora = LocalTime.now();
        return turnos.stream().anyMatch(turno -> {
            LocalTime inicio = turno.getHoraInicio(), fin = turno.getHoraFin();
            return inicio.isBefore(fin) ? (!ahora.isBefore(inicio) && !ahora.isAfter(fin))
                                         : (!ahora.isBefore(inicio) || !ahora.isAfter(fin));
        });
    }

    private static boolean esPrivilegiado(Personal personal) {
        String perfil = perfil(personal);
        return "administrador".equals(perfil) || "seguridad".equals(perfil);
    }

    private static String perfil(Personal personal) {
        if (personal == null || personal.getPerfil() == null || personal.getPerfil().getPerfil() == null) {
            return "";
        }
        return personal.getPerfil().getPerfil().trim().toLowerCase(Locale.ROOT);
    }

    private static String nombrePerfil(Personal personal) {
        if (personal == null || personal.getPerfil() == null || personal.getPerfil().getPerfil() == null) {
            return "";
        }
        return personal.getPerfil().getPerfil().trim();
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String normalizarUsuario(String usuario) {
        return normalizar(usuario).toLowerCase(Locale.ROOT);
    }

    private String tokenAleatorio() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String mensajeRecuperacionGenerico() {
        return MENSAJE_RECUPERACION;
    }
}
