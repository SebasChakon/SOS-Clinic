package com.sos.msseguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class EnvResolucionTest {

    @Autowired
    Environment env;

    /**
     * Valora que el cargador de .env (EnvEnvironmentPostProcessor) registra
     * las variables del repo raiz. Se omite si la maquina no tiene .env
     * (por ejemplo un CI limpio).
     */
    @Test
    void resuelveVariablesDelEnv() {
        boolean hayEnv = new java.io.File(System.getProperty("user.dir"), "../.env").isFile()
                || new java.io.File(System.getProperty("user.dir"), ".env").isFile();
        assumeTrue(hayEnv, "Esta maquina no tiene archivo .env; test omitido");

        assertNotNull(env.getProperty("MAIL_USERNAME"), "MAIL_USERNAME no resolvio desde .env");
        assertNotNull(env.getProperty("MAIL_PASSWORD"), "MAIL_PASSWORD no resolvio desde .env");
    }
}
