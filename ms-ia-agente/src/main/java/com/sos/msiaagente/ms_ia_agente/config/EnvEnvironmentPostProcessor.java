package com.sos.msiaagente.ms_ia_agente.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carga el archivo .env del repo raiz como fuente de propiedades de Spring.
 * Permite lanzar el servicio desde NetBeans o mvn sin exportar variables a mano.
 * Las variables reales del entorno (levantar-sistema.bat) tienen prioridad
 * porque este agregado se coloca al final de la cadena de propiedades.
 */
public class EnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String NOMBRE = "sosEnvFile";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(NOMBRE)) {
            return; // ya cargado (puede registrarse dos veces: spring.factories + .imports)
        }
        Path archivo = buscarEnv();
        if (archivo == null) {
            return;
        }
        Map<String, Object> props = new LinkedHashMap<>();
        try {
            for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                String limpia = linea.trim();
                if (limpia.isEmpty() || limpia.startsWith("#")) {
                    continue;
                }
                int igual = limpia.indexOf('=');
                if (igual <= 0) {
                    continue;
                }
                String clave = limpia.substring(0, igual).trim();
                String valor = limpia.substring(igual + 1).trim();
                if (valor.length() >= 2
                        && ((valor.startsWith("\"") && valor.endsWith("\""))
                        || (valor.startsWith("'") && valor.endsWith("'")))) {
                    valor = valor.substring(1, valor.length() - 1);
                }
                props.put(clave, valor);
            }
        } catch (IOException e) {
            java.util.logging.Logger.getLogger(getClass().getName())
                    .warning("No se pudo leer " + archivo + ": " + e.getMessage());
            return;
        }
        if (!props.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(NOMBRE, props));
        }
    }

    /**
     * Busca .env empezando en el directorio de trabajo (el modulo al lanzar
     * desde NetBeans/mvn) y sube hasta 3 niveles para encontrar el repo raiz.
     */
    private Path buscarEnv() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            Path env = dir.resolve(".env");
            if (Files.isRegularFile(env)) {
                return env;
            }
            dir = dir.getParent();
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
