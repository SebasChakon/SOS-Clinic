package com.sos.msseguridad.modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "registro_auditoria")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_log")
    private Integer idLog;

    @ManyToOne
    @JoinColumn(name = "id_personal", nullable = false)
    private Personal personal;

    @Column(name = "id_paciente")
    private Integer idPaciente;

    @Column(name = "accion", nullable = false, length = 20)
    private String accion;

    @Column(name = "detalle", length = 100)
    private String detalle;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @Column(name = "id_estado", nullable = false)
    private Integer idEstado = 1;
}