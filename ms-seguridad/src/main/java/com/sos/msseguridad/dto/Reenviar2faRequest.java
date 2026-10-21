package com.sos.msseguridad.dto;

import jakarta.validation.constraints.NotBlank;

public class Reenviar2faRequest {

    @NotBlank(message = "El challenge es obligatorio")
    private String challenge;

    public String getChallenge() { return challenge; }
    public void setChallenge(String challenge) { this.challenge = challenge; }
}
