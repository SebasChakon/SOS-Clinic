package com.sos.msseguridad.seguridad;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticaClavesTest {

    private final PoliticaClaves politica = new PoliticaClaves();

    private static PoliticaClaves.Contexto contexto() {
        return new PoliticaClaves.Contexto(
                "Ana", "Perez", "ana.perez@correo.com", "10203040", "3001234567", "aperez", null);
    }

    @Test
    void aceptaUnaClaveQueCumpleTodasLasReglas() {
        assertTrue(politica.validar("Cobre#7391", contexto()).isEmpty());
        assertTrue(politica.validar("Nube_2026-@", PoliticaClaves.Contexto.de(null)).isEmpty());
    }

    @Test
    void rechazaLongitudIncorrecta() {
        assertFalse(politica.validar("Ab1!", null).isEmpty());
        String larga = "Aa1!".repeat(17);
        assertFalse(politica.validar(larga, null).isEmpty());
    }

    @Test
    void rechazaFaltaDeVariedad() {
        assertFalse(politica.validar("abcdef1!", null).isEmpty());
        assertFalse(politica.validar("ABCDEF1!", null).isEmpty());
        assertFalse(politica.validar("Abcdefg!", null).isEmpty());
        assertFalse(politica.validar("Abcdefg1", null).isEmpty());
    }

    @Test
    void rechazaCaracteresFueraDelAlfabeto() {
        assertFalse(politica.validar("Xy1qW&zk", null).isEmpty());
        assertFalse(politica.validar("Nube 2026@", null).isEmpty());
        assertTrue(politica.validar("Contrasena1!", null).stream()
                .anyMatch(e -> e.contains("palabra comun")));
    }

    @Test
    void rechazaRepeticionesYSecuencias() {
        assertTrue(politica.validar("AAA#4821", null).stream().anyMatch(e -> e.contains("tres veces")));
        assertTrue(politica.validar("Abc#4821", null).stream().anyMatch(e -> e.contains("secuencias")));
        assertTrue(politica.validar("Cba#4821", null).stream().anyMatch(e -> e.contains("secuencias")));
        assertTrue(politica.validar("Qwerty12!", null).stream().anyMatch(e -> e.contains("teclado")));
    }

    @Test
    void rechazaPalabrasComunesYLeetspeak() {
        assertTrue(politica.validar("Password1!", null).stream().anyMatch(e -> e.contains("palabra comun")));
        assertTrue(politica.validar("P@ssw0rd1!", null).stream().anyMatch(e -> e.contains("palabra comun")));
        assertTrue(politica.validar("Sosclinic#1", null).stream().anyMatch(e -> e.contains("palabra comun")));
    }

    @Test
    void rechazaDatosPersonalesDelPerfil() {
        List<String> errores = politica.validar("Ana#4821", contexto());
        assertTrue(errores.stream().anyMatch(e -> e.contains("datos personales")));
        assertTrue(politica.validar("Xz#10203040", contexto()).stream()
                .anyMatch(e -> e.contains("datos personales")));
        assertTrue(politica.validar("Cobre#7391", contexto()).isEmpty());
    }

    @Test
    void rechazaLaClaveActual() {
        PoliticaClaves.Contexto conActual = new PoliticaClaves.Contexto(
                "Ana", "Perez", "ana.perez@correo.com", "10203040", "3001234567", "aperez", "Cobre#7391");
        assertTrue(politica.validar("Cobre#7391", conActual).stream()
                .anyMatch(e -> e.contains("igual a la actual")));
    }

    @Test
    void rechazaAcentosYEspacios() {
        assertTrue(politica.validar("Contrasena1!", null).stream()
                .anyMatch(e -> e.contains("palabra comun")));
        assertFalse(politica.validar("Nube 2026@", null).isEmpty());
    }
}
