package com.sos.msseguridad.modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Sesion de aplicacion respaldada por un refresh token opaco.
 * Solo se guarda el SHA-256 del token; el valor en claro existe unicamente
 * en el navegador que lo recibio.
 */
@Entity
@Table(name = "sesion")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Sesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sesion")
    private Integer idSesion;

    @ManyToOne
    @JoinColumn(name = "id_personal", nullable = false)
    private Personal personal;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "ultimo_uso", nullable = false)
    private LocalDateTime ultimoUso = LocalDateTime.now();

    /** Corte por inactividad. Se extiende en cada refresh. */
    @Column(name = "fecha_expira", nullable = false)
    private LocalDateTime fechaExpira;

    /** Tope absoluto de la sesion, aunque el usuario siga activo. */
    @Column(name = "fecha_maxima", nullable = false)
    private LocalDateTime fechaMaxima;

    @Column(name = "revocada", nullable = false)
    private Boolean revocada = false;

    @Column(name = "motivo_revocacion", length = 40)
    private String motivoRevocacion;

    @Column(name = "ip_creacion", length = 45)
    private String ipCreacion;

    @Column(name = "user_agent", length = 200)
    private String userAgent;
}
