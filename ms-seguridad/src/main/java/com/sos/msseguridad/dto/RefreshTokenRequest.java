package com.sos.msseguridad.dto;

/** Cuerpo de /auth/refrescar y /auth/logout. */
public class RefreshTokenRequest {
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
