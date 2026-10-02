package com.sos.mstriage.dto;

import jakarta.validation.constraints.NotBlank;

public class UbicacionRequest {

    @NotBlank
    private String ubicacion;

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }
}
