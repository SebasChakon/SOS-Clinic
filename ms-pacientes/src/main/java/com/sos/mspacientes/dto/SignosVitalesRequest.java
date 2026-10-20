package com.sos.mspacientes.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO de entrada para signos vitales. Deliberadamente no incluye idPersonal,
 * idEstado, idRegistroEstado ni fechaHoraToma: esos valores los decide el
 * servidor (el profesional sale del JWT y la fecha/estado del servidor).
 */
public class SignosVitalesRequest {

    @NotNull(message = "El idRegistro es obligatorio")
    private Integer idRegistro;

    @NotNull(message = "La frecuencia cardiaca es obligatoria")
    @Min(value = 1, message = "La frecuencia cardiaca debe ser positiva")
    @Max(value = 300, message = "La frecuencia cardiaca no es valida")
    private Integer frecuenciaCardiaca;

    @NotNull(message = "La presion sistolica es obligatoria")
    @Min(value = 40, message = "La presion sistolica no es valida")
    @Max(value = 300, message = "La presion sistolica no es valida")
    private Integer presionSistolica;

    @NotNull(message = "La presion diastolica es obligatoria")
    @Min(value = 20, message = "La presion diastolica no es valida")
    @Max(value = 200, message = "La presion diastolica no es valida")
    private Integer presionDiastolica;

    @NotNull(message = "La temperatura es obligatoria")
    @DecimalMin(value = "30.0", message = "La temperatura no es valida")
    @DecimalMax(value = "45.0", message = "La temperatura no es valida")
    private BigDecimal temperatura;

    @NotNull(message = "La saturacion es obligatoria")
    @Min(value = 0, message = "La saturacion no es valida")
    @Max(value = 100, message = "La saturacion no es valida")
    private Integer saturacion;

    @NotNull(message = "La frecuencia respiratoria es obligatoria")
    @Min(value = 1, message = "La frecuencia respiratoria no es valida")
    @Max(value = 100, message = "La frecuencia respiratoria no es valida")
    private Integer frecuenciaRespiratoria;

    @DecimalMin(value = "0.0", message = "La glucemia no es valida")
    @DecimalMax(value = "1000.0", message = "La glucemia no es valida")
    private BigDecimal glucemia;

    @Size(max = 500, message = "La descripcion del sintoma no puede superar 500 caracteres")
    private String descripcionSintoma;

    public Integer getIdRegistro() { return idRegistro; }
    public void setIdRegistro(Integer idRegistro) { this.idRegistro = idRegistro; }
    public Integer getFrecuenciaCardiaca() { return frecuenciaCardiaca; }
    public void setFrecuenciaCardiaca(Integer frecuenciaCardiaca) { this.frecuenciaCardiaca = frecuenciaCardiaca; }
    public Integer getPresionSistolica() { return presionSistolica; }
    public void setPresionSistolica(Integer presionSistolica) { this.presionSistolica = presionSistolica; }
    public Integer getPresionDiastolica() { return presionDiastolica; }
    public void setPresionDiastolica(Integer presionDiastolica) { this.presionDiastolica = presionDiastolica; }
    public BigDecimal getTemperatura() { return temperatura; }
    public void setTemperatura(BigDecimal temperatura) { this.temperatura = temperatura; }
    public Integer getSaturacion() { return saturacion; }
    public void setSaturacion(Integer saturacion) { this.saturacion = saturacion; }
    public Integer getFrecuenciaRespiratoria() { return frecuenciaRespiratoria; }
    public void setFrecuenciaRespiratoria(Integer frecuenciaRespiratoria) { this.frecuenciaRespiratoria = frecuenciaRespiratoria; }
    public BigDecimal getGlucemia() { return glucemia; }
    public void setGlucemia(BigDecimal glucemia) { this.glucemia = glucemia; }
    public String getDescripcionSintoma() { return descripcionSintoma; }
    public void setDescripcionSintoma(String descripcionSintoma) { this.descripcionSintoma = descripcionSintoma; }
}
