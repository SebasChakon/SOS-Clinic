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
@Table(name = "categoria_triage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaTriage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria_triage")
    private Integer idCategoriaTriage;

    @Column(name = "nombre_categoria", nullable = false, unique = true, length = 30)
    private String nombreCategoria;

    @Column(name = "descripcion", length = 100)
    private String descripcion;

    @Column(name = "prioridad", nullable = false, unique = true)
    private Integer prioridad;

    @Column(name = "id_estado", nullable = false)
    private Integer idEstado = 1;
}
