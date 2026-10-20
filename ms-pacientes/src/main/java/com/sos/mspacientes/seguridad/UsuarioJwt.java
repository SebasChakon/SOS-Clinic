package com.sos.mspacientes.seguridad;

import java.security.Principal;
import java.util.List;

public record UsuarioJwt(String usuario, Integer idPersonal, String perfil,
                         List<String> actividades) implements Principal {

    @Override
    public String getName() {
        return usuario;
    }
}
