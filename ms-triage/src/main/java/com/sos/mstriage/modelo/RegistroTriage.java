/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mstriage.modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "registro_triage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroTriage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registro")
    private Integer idRegistro;

    @Column(name = "id_paciente", nullable = false)
    private Integer idPaciente; // referencia a paciente.id_paciente (dueño: ms-pacientes)

    @ManyToOne
    @JoinColumn(name = "id_estado_triage", nullable = false)
    private EstadoTriage estadoTriage;

    @ManyToOne
    @JoinColumn(name = "id_categoria_triage", nullable = false)
    private CategoriaTriage categoriaTriage;

    @Column(name = "tiempo_espera_minutos")
    private Integer tiempoEsperaMinutos;

    @Column(name = "ubicacion", length = 50)
    private String ubicacion;

    @Column(name = "fecha_hora_llegada", nullable = false)
    private LocalDateTime fechaHoraLlegada = LocalDateTime.now();

    @Column(name = "id_estado", nullable = false)
    private Integer idEstado = 1;
    
    @Column(name = "diagnostico_final", length = 1000)
    private String diagnosticoFinal;

    @Column(name = "tratamiento", length = 1000)
    private String tratamiento;
}
