/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "codigo_verificacion")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodigoVerificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_codigo")
    private Integer idCodigo;

    @ManyToOne
    @JoinColumn(name = "id_personal", nullable = false)
    private Personal personal;

    @Column(name = "codigo", nullable = false, length = 6)
    private String codigo;

    @Column(name = "fecha_expira", nullable = false)
    private LocalDateTime fechaExpira;

    @Column(name = "usado", nullable = false)
    private Boolean usado = false;
}
