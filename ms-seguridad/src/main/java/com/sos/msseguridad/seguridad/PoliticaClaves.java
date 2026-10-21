package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.modelo.Personal;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unica fuente de verdad para la politica de claves.
 *
 * Reglas: 8 a 64 caracteres, solo letras ASCII, digitos y los simbolos
 * ! ? # $ % . * @ _ - +, con al menos una mayuscula, una minuscula, un
 * digito y un simbolo. Ademas rechaza repeticiones, secuencias, patrones
 * de teclado, palabras comunes y datos personales del propio perfil.
 */
@Component
public class PoliticaClaves {

    public static final int LONGITUD_MINIMA = 8;
    public static final int LONGITUD_MAXIMA = 64;
    public static final String SIMBOLOS = "!?#$%.*@_+-";

    private static final Pattern ALFABETO = Pattern.compile("^[A-Za-z0-9!?#$%.*@_+-]+$");
    private static final Pattern MAYUSCULA = Pattern.compile("[A-Z]");
    private static final Pattern MINUSCULA = Pattern.compile("[a-z]");
    private static final Pattern DIGITO = Pattern.compile("[0-9]");
    private static final Pattern SIMBOLO = Pattern.compile("[!?#$%.*@_+-]");
    private static final Pattern REPETICION = Pattern.compile("(.)\\1\\1");

    private static final List<String> SECUENCIAS_TECLADO = List.of(
            "qwerty", "qwert", "asdf", "asdfg", "zxcv", "zxcvb",
            "qaz", "wsx", "edc", "rfv", "tgb", "yhn", "ujm",
            "qwe", "wer", "ert", "rty", "tyu", "yui", "uio", "iop",
            "asd", "sdf", "dfg", "fgh", "ghj", "hjk", "jkl",
            "zxc", "xcv", "cvb", "vbn", "bnm"
    );

    private static final Set<String> PALABRAS_COMUNES = Set.copyOf(List.of(
            "password", "contrasena", "contrasenya", "passw0rd", "clave",
            "admin", "administrador", "usuario", "user", "login", "root",
            "qwerty", "asdf", "zxcv", "abc123", "123456", "12345678", "123456789",
            "111111", "000000", "654321", "iloveyou", "teamo", "amor",
            "welcome", "bienvenido", "bienvenida", "monkey", "dragon", "master",
            "sunshine", "princess", "shadow", "superman", "batman", "football",
            "baseball", "letmein", "trustno1", "hello", "hola", "secret", "secreto",
            "sosclinic", "sosclinic2026", "hospital", "clinica", "clinic",
            "medico", "medica", "doctor", "doctora", "enfermera", "enfermero",
            "triage", "urgencias", "urgencia", "paciente", "salud",
            "colombia", "bogota", "cundinamarca", "sistema", "soporte",
            "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre",
            "verano", "invierno", "primavera", "otono"
    ));

    /** Datos del perfil contra los que se compara la clave candidata. */
    public record Contexto(String nombre, String apellido, String email, String cedula,
                           String telefono, String usuario, String claveActual) {

        public static Contexto de(Personal personal) {
            return de(personal, null);
        }

        public static Contexto de(Personal personal, String claveActual) {
            if (personal == null) return null;
            return new Contexto(personal.getNombre(), personal.getApellido(), personal.getEmail(),
                    personal.getCedula(), personal.getTelefono(), personal.getUsuario(), claveActual);
        }
    }

    public List<String> validar(String clave, Contexto contexto) {
        List<String> errores = new ArrayList<>();

        if (clave == null || clave.isBlank()) {
            errores.add("La clave es obligatoria.");
            return errores;
        }
        if (clave.length() < LONGITUD_MINIMA) {
            errores.add("Debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }
        if (clave.length() > LONGITUD_MAXIMA) {
            errores.add("Debe tener como maximo " + LONGITUD_MAXIMA + " caracteres.");
        }
        if (!ALFABETO.matcher(clave).matches()) {
            errores.add("Solo se permiten letras sin tilde, numeros y los simbolos " + SIMBOLOS + ".");
        }
        if (!MAYUSCULA.matcher(clave).find()) {
            errores.add("Debe incluir al menos una letra mayuscula.");
        }
        if (!MINUSCULA.matcher(clave).find()) {
            errores.add("Debe incluir al menos una letra minuscula.");
        }
        if (!DIGITO.matcher(clave).find()) {
            errores.add("Debe incluir al menos un numero.");
        }
        if (!SIMBOLO.matcher(clave).find()) {
            errores.add("Debe incluir al menos un simbolo de " + SIMBOLOS + ".");
        }
        if (REPETICION.matcher(clave).find()) {
            errores.add("No repitas el mismo caracter tres veces seguidas.");
        }
        if (tieneSecuencia(clave)) {
            errores.add("No uses secuencias como abc, 123, cba o 321.");
        }
        String enMinuscula = clave.toLowerCase(Locale.ROOT);
        if (SECUENCIAS_TECLADO.stream().anyMatch(enMinuscula::contains)) {
            errores.add("No uses secuencias de teclado como qwerty o asdf.");
        }

        String normalizada = normalizarParaComparar(clave);
        if (contienePalabraComun(normalizada)) {
            errores.add("La clave contiene una palabra comun o el nombre del sistema.");
        }
        if (contexto != null) {
            if (contieneDatoPersonal(normalizada, contexto)) {
                errores.add("La clave no puede contener tus datos personales (nombre, apellido, usuario, correo, cedula o telefono).");
            }
            if (contexto.claveActual() != null && clave.equals(contexto.claveActual())) {
                errores.add("La nueva clave no puede ser igual a la actual.");
            }
        }

        return errores;
    }

    private static boolean tieneSecuencia(String clave) {
        for (int i = 0; i + 2 < clave.length(); i++) {
            char a = Character.toLowerCase(clave.charAt(i));
            char b = Character.toLowerCase(clave.charAt(i + 1));
            char c = Character.toLowerCase(clave.charAt(i + 2));
            boolean digitos = Character.isDigit(a) && Character.isDigit(b) && Character.isDigit(c);
            boolean letras = Character.isLetter(a) && Character.isLetter(b) && Character.isLetter(c);
            if (!digitos && !letras) continue;
            if ((b - a == 1 && c - b == 1) || (b - a == -1 && c - b == -1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contienePalabraComun(String normalizada) {
        for (String palabra : PALABRAS_COMUNES) {
            if (normalizada.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contieneDatoPersonal(String claveNormalizada, Contexto contexto) {
        Set<String> datos = new LinkedHashSet<>();
        agregarDatos(datos, contexto.nombre());
        agregarDatos(datos, contexto.apellido());
        agregarDatos(datos, contexto.usuario());
        agregarDatos(datos, contexto.email());
        if (contexto.email() != null) {
            int arroba = contexto.email().indexOf('@');
            if (arroba > 0) agregarDatos(datos, contexto.email().substring(0, arroba));
        }
        if (contexto.nombre() != null && contexto.apellido() != null) {
            agregarDatos(datos, contexto.nombre() + contexto.apellido());
            agregarDatos(datos, contexto.apellido() + contexto.nombre());
        }
        agregarDatos(datos, contexto.cedula());
        agregarDatos(datos, contexto.telefono());
        agregarUltimosCuatro(datos, contexto.cedula());
        agregarUltimosCuatro(datos, contexto.telefono());

        return datos.stream().anyMatch(claveNormalizada::contains);
    }

    private static void agregarDatos(Set<String> destino, String valor) {
        if (valor == null) return;
        String normalizado = normalizarParaComparar(valor);
        if (normalizado.length() >= 3) destino.add(normalizado);
        for (String parte : normalizado.split("[^a-z0-9]+")) {
            if (parte.length() >= 3) {
                destino.add(parte);
                destino.add(new StringBuilder(parte).reverse().toString());
            }
        }
    }

    private static void agregarUltimosCuatro(Set<String> destino, String valor) {
        if (valor == null) return;
        String soloDigitos = valor.replaceAll("\\D", "");
        if (soloDigitos.length() >= 4) {
            destino.add(soloDigitos.substring(soloDigitos.length() - 4));
        }
    }

    /**
     * Minusculas, sin tildes, con sustituciones habituales (leetspeak) y sin
     * separadores. Sirve para comparar contra palabras comunes y datos propios.
     */
    static String normalizarParaComparar(String valor) {
        if (valor == null) return "";
        String sinTildes = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String minuscula = sinTildes.toLowerCase(Locale.ROOT);
        StringBuilder salida = new StringBuilder(minuscula.length());
        for (char c : minuscula.toCharArray()) {
            switch (c) {
                case '@' -> salida.append('a');
                case '0' -> salida.append('o');
                case '1' -> salida.append('i');
                case '3' -> salida.append('e');
                case '$' -> salida.append('s');
                case '#' -> salida.append('h');
                case '!' -> salida.append('i');
                case '+' -> salida.append('t');
                default -> {
                    if (Character.isLetterOrDigit(c)) salida.append(c);
                }
            }
        }
        return salida.toString();
    }
}
