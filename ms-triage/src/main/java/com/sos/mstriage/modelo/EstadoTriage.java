/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mstriage.modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "estado_triage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadoTriage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_triage")
    private Integer idEstadoTriage;

    @Column(name = "nombre_estado", nullable = false, unique = true, length = 30)
    private String nombreEstado;

    @Column(name = "descripcion", length = 100)
    private String descripcion;
}
