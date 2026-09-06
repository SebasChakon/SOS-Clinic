/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mspacientes.modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "estado_paciente")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadoPaciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registro_estado")
    private Integer idRegistroEstado;

    @Column(name = "id_registro", nullable = false)
    private Integer idRegistro; // referencia a registro_triage.id_registro (dueño: ms-triage)

    @Column(name = "frecuencia_cardiaca", nullable = false)
    private Integer frecuenciaCardiaca;

    @Column(name = "presion_sistolica", nullable = false)
    private Integer presionSistolica;

    @Column(name = "presion_diastolica", nullable = false)
    private Integer presionDiastolica;

    @Column(name = "temperatura", nullable = false)
    private BigDecimal temperatura;

    @Column(name = "saturacion", nullable = false)
    private Integer saturacion;

    @Column(name = "frecuencia_respiratoria", nullable = false)
    private Integer frecuenciaRespiratoria;

    @Column(name = "glucemia")
    private BigDecimal glucemia;

    @Column(name = "descripcion_sintoma", length = 500)
    private String descripcionSintoma;

    @Column(name = "fecha_hora_toma", nullable = false)
    private LocalDateTime fechaHoraToma = LocalDateTime.now();

    @Column(name = "id_personal", nullable = false)
    private Integer idPersonal; // referencia a personal.id_personal (dueño: ms-seguridad)

    @Column(name = "id_estado", nullable = false)
    private Integer idEstado = 1;
}
