package com.sos.msseguridad.seguridad;

import java.security.Principal;
import java.util.List;

/**
 * Identidad extraida de un JWT valido. Implementa Principal para que los
 * controladores que reciben java.security.Principal sigan funcionando.
 */
public record UsuarioJwt(String usuario, Integer idPersonal, String perfil,
                         List<String> actividades) implements Principal {

    @Override
    public String getName() {
        return usuario;
    }
}
