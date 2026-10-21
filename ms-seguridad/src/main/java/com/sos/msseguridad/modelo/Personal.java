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
@Table(name = "personal")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Personal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_personal")
    private Integer idPersonal;

    @Column(name = "nombre", nullable = false, length = 30)
    private String nombre;

    @Column(name = "apellido", nullable = false, length = 30)
    private String apellido;

    @Column(name = "email", nullable = false, unique = true, length = 60)
    private String email;

    @Column(name = "cedula", nullable = false, unique = true, length = 12)
    private String cedula;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "usuario", nullable = false, unique = true, length = 20)
    private String usuario;

    @Column(name = "clave", nullable = false, length = 255)
    private String clave; // hash BCrypt, nunca texto plano

    @ManyToOne
    @JoinColumn(name = "id_perfil", nullable = false)
    private Perfil perfil;

    @Column(name = "id_estado", nullable = false)
    private Integer idEstado = 1;

    @Column(name = "requiere_2fa", nullable = false)
    private Boolean requiere2fa = false;

    @Column(name = "debe_cambiar_clave", nullable = false)
    private Boolean debeCambiarClave = false;

    @Column(name = "clave_expira")
    private LocalDateTime claveExpira;

    @Column(name = "fecha_cambio_clave")
    private LocalDateTime fechaCambioClave;

    @Column(name = "estado_aprobacion", nullable = false, length = 20)
    private String estadoAprobacion = "APROBADA";

    @Column(name = "aprobado_por")
    private Integer aprobadoPor;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "email_pendiente", length = 60)
    private String emailPendiente;
}
