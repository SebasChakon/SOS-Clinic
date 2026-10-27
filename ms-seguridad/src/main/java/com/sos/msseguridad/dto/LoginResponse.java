package com.sos.msseguridad.dto;

public class LoginResponse {
    private String token;
    private String usuario;
    private String perfil;
    private Integer idPersonal;
    private java.util.List<ActividadDTO> actividades;
    private Boolean requiere2fa;
    private String challenge;
    private Boolean debeCambiarClave;
    private String refreshToken;
    private Long tokenExpiraEn;
    private Long sesionExpiraEn;

    public LoginResponse(String token, String usuario, String perfil, Integer idPersonal,
                          java.util.List<ActividadDTO> actividades, Boolean requiere2fa,
                          String challenge, Boolean debeCambiarClave) {
        this(token, usuario, perfil, idPersonal, actividades, requiere2fa, challenge,
                debeCambiarClave, null, null, null);
    }

    public LoginResponse(String token, String usuario, String perfil, Integer idPersonal,
                          java.util.List<ActividadDTO> actividades, Boolean requiere2fa,
                          String challenge, Boolean debeCambiarClave,
                          String refreshToken, Long tokenExpiraEn, Long sesionExpiraEn) {
        this.token = token;
        this.usuario = usuario;
        this.perfil = perfil;
        this.idPersonal = idPersonal;
        this.actividades = actividades;
        this.requiere2fa = requiere2fa;
        this.challenge = challenge;
        this.debeCambiarClave = debeCambiarClave;
        this.refreshToken = refreshToken;
        this.tokenExpiraEn = tokenExpiraEn;
        this.sesionExpiraEn = sesionExpiraEn;
    }

    public String getToken() { return token; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public Integer getIdPersonal() { return idPersonal; }
    public java.util.List<ActividadDTO> getActividades() { return actividades; }
    public Boolean getRequiere2fa() { return requiere2fa; }
    public String getChallenge() { return challenge; }
    public Boolean getDebeCambiarClave() { return debeCambiarClave; }
    public String getRefreshToken() { return refreshToken; }
    public Long getTokenExpiraEn() { return tokenExpiraEn; }
    public Long getSesionExpiraEn() { return sesionExpiraEn; }
}
