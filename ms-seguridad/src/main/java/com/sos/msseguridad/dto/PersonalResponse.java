package com.sos.msseguridad.dto;

import com.sos.msseguridad.modelo.Personal;

public record PersonalResponse(Integer idPersonal, String nombre, String apellido, String email,
                               String cedula, String telefono, String usuario, Integer idPerfil,
                               String perfil, Integer idEstado, Boolean requiere2fa,
                               String estadoAprobacion, String emailPendiente,
                               Boolean debeCambiarClave, Boolean activada) {

    public static PersonalResponse de(Personal p) {
        if (p == null) return null;
        return new PersonalResponse(
                p.getIdPersonal(), p.getNombre(), p.getApellido(), p.getEmail(),
                p.getCedula(), p.getTelefono(), p.getUsuario(),
                p.getPerfil() == null ? null : p.getPerfil().getIdPerfil(),
                p.getPerfil() == null ? null : p.getPerfil().getPerfil(),
                p.getIdEstado(), p.getRequiere2fa(), p.getEstadoAprobacion(),
                p.getEmailPendiente(), p.getDebeCambiarClave(),
                p.getFechaCambioClave() != null);
    }
}
